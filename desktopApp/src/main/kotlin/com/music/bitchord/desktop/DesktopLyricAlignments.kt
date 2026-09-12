package com.music.bitchord.desktop

/** `ttm:agent` types, as both TTML and LyricsPlus name them. */
private const val PERSON = "person"
private const val GROUP = "group"
private const val OTHER = "other"

/** Apple's two reserved voices: everyone at once, and the other singer. */
private const val GROUP_AGENT = "v1000"
private const val OTHER_AGENT = "v2000"

/**
 * Past this share of lines on the right, the whole song is flipped — see [desktopLineAlignments].
 */
private const val MOSTLY_RIGHT = 0.85f

/** Which side of the panel each line is sung from, given the voice that sang it. */
internal fun desktopLineAlignments(
    singers: List<String?>,
    types: Map<String, String>,
): List<DesktopLyricAlignment> {
    var left = true
    var lastVoice: String? = null
    var rightward = 0
    var placed = 0

    val sides = singers.map { singer ->
        if (singer.isNullOrEmpty()) return@map DesktopLyricAlignment.Start
        // Apple's two reserved ids carry no declaration of their own.
        val type = types[singer] ?: when (singer) {
            GROUP_AGENT -> GROUP
            OTHER_AGENT -> OTHER
            else -> PERSON
        }
        placed += 1
        if (type == GROUP) return@map DesktopLyricAlignment.Start

        when {
            lastVoice == null -> left = type != OTHER
            singer != lastVoice -> left = !left
        }
        lastVoice = singer

        if (!left) rightward += 1
        if (left) DesktopLyricAlignment.Start else DesktopLyricAlignment.End
    }

    if (placed == 0 || rightward.toFloat() / placed < MOSTLY_RIGHT) return sides
    return sides.map {
        if (it == DesktopLyricAlignment.Start) DesktopLyricAlignment.End else DesktopLyricAlignment.Start
    }
}
