package com.music.bitchord.ui.tv.player

import androidx.compose.runtime.Immutable
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord

@Immutable
data class LyricsSyncState(
    val activeLineIndex: Int = -1,
    val activeWordIndex: Int = -1,
    val wordProgressFraction: Float = 0f,
    val isInstrumentalGap: Boolean = false,
    val hasLyrics: Boolean = false,
)

object LyricsSynchronizer {

    /**
     * Efficiently calculates the current active line and word position based on playback time.
     * Uses binary search on line timestamps to maintain 60fps/120fps smooth TV rendering.
     */
    fun synchronize(
        lines: List<LyricLine>?,
        currentPositionMs: Long,
    ): LyricsSyncState {
        if (lines.isNullOrEmpty()) {
            return LyricsSyncState(hasLyrics = false)
        }

        // Binary search for the active line whose timestamp is <= currentPositionMs
        var low = 0
        var high = lines.size - 1
        var lineIndex = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val lineTime = lines[mid].timeMs
            if (lineTime <= currentPositionMs) {
                lineIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        if (lineIndex == -1) {
            return LyricsSyncState(
                activeLineIndex = 0,
                activeWordIndex = -1,
                wordProgressFraction = 0f,
                isInstrumentalGap = lines.firstOrNull()?.isGap ?: false,
                hasLyrics = true,
            )
        }

        val currentLine = lines[lineIndex]
        val isGap = currentLine.isGap

        // Calculate word-level synchronization if words are present
        var wordIndex = -1
        var wordFraction = 0f

        if (currentLine.words.isNotEmpty()) {
            for (i in currentLine.words.indices) {
                val word = currentLine.words[i]
                if (currentPositionMs >= word.startMs && currentPositionMs <= word.endMs) {
                    wordIndex = i
                    val duration = (word.endMs - word.startMs).coerceAtLeast(1L)
                    wordFraction = ((currentPositionMs - word.startMs).toFloat() / duration.toFloat())
                        .coerceIn(0f, 1f)
                    break
                } else if (currentPositionMs > word.endMs) {
                    wordIndex = i
                    wordFraction = 1f
                }
            }
        }

        return LyricsSyncState(
            activeLineIndex = lineIndex,
            activeWordIndex = wordIndex,
            wordProgressFraction = wordFraction,
            isInstrumentalGap = isGap,
            hasLyrics = true,
        )
    }
}
