package com.music.bitchord.desktop

import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder_by_name
import java.util.concurrent.ConcurrentHashMap

/** What this build can actually decode, asked of the decoder itself. */
internal object DesktopCodecs {

    private val known = ConcurrentHashMap<String, Boolean>()

    /** Whether libavcodec holds a decoder registered under [name]. */
    fun canDecode(name: String): Boolean =
        known.getOrPut(name) { avcodec_find_decoder_by_name(name) != null }

    /** Whether a Dolby Atmos rendition can be played. */
    val supportsDolbyAtmos: Boolean get() = canDecode("eac3")

    /** Every codec the Android build can play, by FFmpeg's name for it. */
    val EXPECTED = listOf(
        // YouTube serves these two and nothing else.
        "aac",
        "opus",
        // Sources and local files.
        "flac",
        "alac",
        "mp3",
        "vorbis",
        "pcm_s16le",
        "pcm_s24le",
        "pcm_f32le",
        // Dolby, for the Atmos renditions sources offer at their top tier.
        "eac3",
        "ac3",
    )

    /** Those of [EXPECTED] this build is missing, for a log line at startup. */
    fun missing(): List<String> = EXPECTED.filterNot(::canDecode)
}
