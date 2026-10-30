package com.music.bitchord.data.lyrics

import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.Test

class LyricsPickTest {

    private fun result(source: LyricsSource, timeMs: Long) =
        LyricsRepository.Result(source, listOf(LyricLine(timeMs = timeMs, text = "line")))

    @Test
    fun `plain lyrics found first do not shadow line-synced ones when syllable sync is preferred`() = runBlocking {
        val picked = LyricsRepository.pick(
            listOf(
                LyricsSource.YOUTUBE_MUSIC to suspend { result(LyricsSource.YOUTUBE_MUSIC, 0L) },
                LyricsSource.LRCLIB to suspend { result(LyricsSource.LRCLIB, 1_000L) },
            ),
            prioritizeSyllableSync = true,
        )

        assertEquals(LyricsSource.LRCLIB, picked?.source)
    }

    @Test
    fun `plain lyrics are still the answer when nothing is timed`() = runBlocking {
        val picked = LyricsRepository.pick(
            listOf(LyricsSource.YOUTUBE_MUSIC to suspend { result(LyricsSource.YOUTUBE_MUSIC, 0L) }),
            prioritizeSyllableSync = true,
        )

        assertEquals(LyricsSource.YOUTUBE_MUSIC, picked?.source)
    }
}
