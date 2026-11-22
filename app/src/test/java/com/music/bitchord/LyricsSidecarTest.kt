package com.music.bitchord

import com.music.bitchord.data.lyrics.LyricsSidecar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sidecar's two pure decisions, kept here so they can be checked without a
 * device: which file a track's lyrics belong in, and whether a string is
 * actually timed. The second is what stops a provider's plain text from being
 * written as if it were synchronized.
 */
class LyricsSidecarTest {

    @Test
    fun `a track's sidecar takes its stem and the lrc extension`() {
        assertEquals("Song.lrc", LyricsSidecar.lrcNameFor("Song.mp3"))
        assertEquals("Song.lrc", LyricsSidecar.lrcNameFor("Song.flac"))
        assertEquals("Song.lrc", LyricsSidecar.lrcNameFor("dir/Song.m4a"))
        assertEquals("Song.lrc", LyricsSidecar.lrcNameFor("dir\\Song.m4a"))
    }

    @Test
    fun `dots in the middle of a name are kept`() {
        assertEquals("Song (live 1999).lrc", LyricsSidecar.lrcNameFor("Song (live 1999).mp3"))
        assertEquals("track.01.lrc", LyricsSidecar.lrcNameFor("track.01.flac"))
    }

    @Test
    fun `a name with no extension still gets one`() {
        assertEquals("Song.lrc", LyricsSidecar.lrcNameFor("Song"))
    }

    @Test
    fun `a blank name falls back to a usable sidecar name`() {
        assertEquals("lyrics.lrc", LyricsSidecar.lrcNameFor(""))
        assertEquals("lyrics.lrc", LyricsSidecar.lrcNameFor(".mp3"))
    }

    @Test
    fun `a bare timestamp is enough to be synchronized`() {
        assertTrue(LyricsSidecar.hasTimestamp("[00:12.00]a line"))
        assertTrue(LyricsSidecar.hasTimestamp("<00:12.00>word"))
        assertTrue(LyricsSidecar.hasTimestamp("[1:02:03.50]a long stamp"))
        assertTrue(LyricsSidecar.hasTimestamp("[00:12]a short stamp"))
    }

    @Test
    fun `plain text and unsynchronized metadata are not timestamps`() {
        assertFalse(LyricsSidecar.hasTimestamp("a line with no stamp"))
        assertFalse(LyricsSidecar.hasTimestamp("[ar:Artist]\n[ti:Title]\na line"))
        assertFalse(LyricsSidecar.hasTimestamp("[00:aa.00]not a stamp"))
    }
}
