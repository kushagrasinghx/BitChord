package com.music.bitchord.desktop

import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** A change in the lyric settings must not be answered from the cache filled under the old ones. */
class DesktopLyricsCacheKeyTest {
    private val song = Song("abcdefghijk", "Song", "Artist", null)
    private val chain = listOf(LyricsSource.LRCLIB, LyricsSource.GENIUS)

    private fun key(asked: List<LyricsSource> = chain, prioritize: Boolean = false) =
        DesktopLyricsClient.cacheKey(song, 180_000, null, asked, prioritize)

    @Test
    fun theSameLookupSharesAnEntry() {
        assertEquals(key(), key())
    }

    @Test
    fun preferringSyllableTimingIsAnotherEntry() {
        assertNotEquals(key(prioritize = false), key(prioritize = true))
    }

    @Test
    fun reorderingOrUntickingSourcesIsAnotherEntry() {
        assertNotEquals(key(), key(asked = chain.reversed()))
        assertNotEquals(key(), key(asked = listOf(LyricsSource.LRCLIB)))
    }
}
