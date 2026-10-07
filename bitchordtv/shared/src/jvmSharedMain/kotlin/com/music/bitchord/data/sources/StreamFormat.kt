package com.music.bitchord.data.sources

import java.util.Locale

/**
 * What a source is about to hand the decoder, as far as the source will say.
 *
 * Every field is nullable because most of them are genuinely unknown until the
 * bytes arrive: a server that reports "flac" rarely reports the bit depth with
 * it, and one that reports a bitrate is usually describing a transcode it has
 * not performed yet. Nothing here is inferred — a null means "not stated", and
 * the player reports the decoder's own numbers over these once it has them.
 * The two are worth comparing rather than merging, because a source claiming
 * 24/192 and a sink running at 16/48 is exactly the failure this feature is
 * most likely to hide.
 */
data class StreamFormat(
    /** Container/codec as the source names it, lowercased: `flac`, `opus`, `mp3`. */
    val codec: String? = null,
    val kbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
) {
    /**
     * Whether this is a bit-exact copy of the master the source holds.
     *
     * Decided on the codec alone. A lossless codec at any bitrate is lossless;
     * a lossy one at any bitrate is not, and no sample rate rescues it — a
     * 192kHz Opus stream is still Opus. Unknown codec means unknown, not false,
     * so callers that care have to say what they want done about it.
     */
    val isLossless: Boolean?
        get() = codec?.let { it in LOSSLESS_CODECS }

    /** Dolby Atmos carried in E-AC-3 JOC. This is immersive, but not lossless. */
    val isDolbyAtmos: Boolean
        get() = codec in DOLBY_ATMOS_CODECS

    /** "24-bit · 192 kHz", "FLAC", "320 kbps" — whichever parts are known. */
    val summary: String
        get() = if (isDolbyAtmos) "Dolby Atmos" else listOfNotNull(
            codec?.uppercase(Locale.ROOT),
            bitDepth?.let { "$it-bit" },
            sampleRateHz?.let { "${"%.1f".format(Locale.ROOT, it / 1000f).removeSuffix(".0")} kHz" },
            kbps?.takeIf { isLossless != true }?.let { "$it kbps" },
        ).joinToString(" · ").ifEmpty { "Unknown format" }

    private companion object {
        val LOSSLESS_CODECS = setOf("flac", "alac", "wav", "aiff", "ape", "wv", "dsf", "dff")
        val DOLBY_ATMOS_CODECS = setOf("eac3-joc", "ec3-joc", "dolby-atmos")
    }
}
