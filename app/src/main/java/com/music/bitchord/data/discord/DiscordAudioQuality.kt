package com.music.bitchord.data.discord

import com.music.bitchord.data.NerdStats
import java.util.Locale

/**
 * The optional audio line shown on BitChord's Discord activity card.
 *
 * Only premium formats earn a line. The values are the decoder's measured
 * figures from [NerdStats], never the quality a source merely advertised, so a
 * lossy fallback cannot continue calling itself Lossless on Discord.
 */
fun discordAudioQualityLine(stats: NerdStats.Snapshot?): String? {
    stats ?: return null

    val quality = when {
        stats.isDolbyAtmos -> "◗◖ Dolby Atmos"
        stats.isHiRes -> "Hi-Res Lossless"
        stats.isLossless -> "Lossless"
        else -> return null
    }

    return buildList {
        add(quality)
        NerdStats.codecLabel(stats.mimeType)?.let(::add)
        stats.bitrateKbps?.takeIf { it > 0 }?.let { add("$it kbps") }
        stats.bitDepth?.takeIf { it > 0 }?.let { add("$it-bit") }
        stats.sampleRateHz?.takeIf { it > 0 }?.let {
            val khz = String.format(Locale.ROOT, "%.1f", it / 1000f).removeSuffix(".0")
            add("$khz kHz")
        }
        stats.channels?.takeIf { it > 0 }?.let {
            add(
                when (it) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    else -> "$it ch"
                },
            )
        }
    }.joinToString(" · ").take(DISCORD_TEXT_LIMIT).trimEnd()
}

/** Discord caps each activity text field at 128 characters. */
private const val DISCORD_TEXT_LIMIT = 128
