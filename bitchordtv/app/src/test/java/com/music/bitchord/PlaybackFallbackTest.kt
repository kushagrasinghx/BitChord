package com.music.bitchord

import com.music.bitchord.playback.PlaybackFallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFallbackTest {

    @Test
    fun `failed upgraded YouTube item is an alternative`() {
        assertTrue(
            PlaybackFallback.isAlternative(
                "bitchord://watch?v=5IX-nUxDtJI&n=Bin%20Tere&q=hifi",
                substitutedYouTube = false,
            ),
        )
    }

    @Test
    fun `failed substituted base item is an alternative`() {
        assertTrue(
            PlaybackFallback.isAlternative(
                "bitchord://watch?v=5IX-nUxDtJI&n=Bin%20Tere",
                substitutedYouTube = true,
            ),
        )
    }

    @Test
    fun `direct YouTube fallback never falls back recursively`() {
        assertFalse(
            PlaybackFallback.isAlternative(
                "bitchord://watch?v=5IX-nUxDtJI&direct_youtube=1&q=original",
                substitutedYouTube = true,
            ),
        )
    }

    @Test
    fun `ordinary YouTube audio is not treated as a failed alternative`() {
        assertFalse(
            PlaybackFallback.isAlternative(
                "bitchord://watch?v=5IX-nUxDtJI&n=Bin%20Tere",
                substitutedYouTube = false,
            ),
        )
    }

    @Test
    fun `source already forced to YouTube never falls back recursively`() {
        assertFalse(
            PlaybackFallback.isAlternative(
                "bitchord://source?s=addon-1&t=3919244&n=Bin%20Tere&direct_youtube=1&q=original",
                substitutedYouTube = false,
            ),
        )
    }

    @Test
    fun `source fallback removes manifest and upgrade markers and gets its own rendition`() {
        val failed =
            "bitchord://source?s=addon-1&t=3919244&n=Bin%20Tere&a=Vishal%20%26%20Shekhar" +
                "&manifest_reopen=dash&q=hifi#part"

        assertEquals(
            "bitchord://source?s=addon-1&t=3919244&n=Bin%20Tere&a=Vishal%20%26%20Shekhar" +
                "&direct_youtube=1&q=original#part",
            PlaybackFallback.directYouTubeSourceUri(failed),
        )
    }
}
