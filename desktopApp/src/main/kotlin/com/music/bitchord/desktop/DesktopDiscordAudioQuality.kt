package com.music.bitchord.desktop

import com.music.bitchord.data.NerdStats
import java.util.Locale

fun desktopAudioQualityLine(stats: NerdStats.Snapshot?): String? {
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
            add(NerdStats.megahertzLabel(it) ?: "$khz kHz")
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

private const val DISCORD_TEXT_LIMIT = 128
