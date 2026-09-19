package com.music.bitchord

import com.music.bitchord.data.lyrics.EnhancedLrc
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.LyricsArtifact
import com.music.bitchord.data.lyrics.LyricsArtifactFormat
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSerializer
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.toLrc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsArtifactTest {

    @Test
    fun `serialises word timing as enhanced lrc and parses it back`() {
        val lines = listOf(
            LyricLine(
                timeMs = 1_230L,
                text = "hello world",
                words = listOf(
                    LyricWord(1_230L, 1_600L, "hello"),
                    LyricWord(1_700L, 2_100L, "world"),
                ),
            ),
        )

        val artifact = LyricsSerializer.fromLines(LyricsSource.LYRICS_PLUS, lines)
        assertNotNull(artifact)
        val parsed = EnhancedLrc.parse(artifact!!.content)

        assertEquals(LyricsArtifactFormat.ENHANCED_LRC, artifact.format)
        assertTrue(artifact.isWordSynced)
        assertEquals("hello world", parsed.single().text)
        assertEquals(1_230L, parsed.single().words.first().startMs)
        assertEquals(1_700L, parsed.single().words.last().startMs)
    }

    @Test
    fun `offline parser selects the format-specific parser`() {
        val result = LyricsRepository.offline(
            content = "[00:01.34]line",
            format = LyricsArtifactFormat.LRC,
            source = LyricsSource.LRCLIB,
        )

        assertEquals(LyricsSource.LRCLIB, result?.source)
        assertEquals("line", result?.lines?.single()?.text)
        assertEquals(LyricsArtifactFormat.LRC, result?.artifact?.format)
    }

    @Test
    fun `plain text keeps instrumental gaps without timestamps`() {
        val lines = listOf(LyricLine(0L, ""), LyricLine(1_000L, "words"))

        assertEquals("\nwords", LyricsSerializer.plainText(lines))
    }

    @Test
    fun `sidecar filename derives stem from audio name`() {
        val artifactLrc = LyricsArtifact(
            source = LyricsSource.LRCLIB,
            format = LyricsArtifactFormat.LRC,
            content = "[00:01.00]test",
            lines = listOf(LyricLine(1000L, "test")),
        )
        val artifactTtml = LyricsArtifact(
            source = LyricsSource.BETTER_LYRICS,
            format = LyricsArtifactFormat.TTML,
            content = "<tt></tt>",
            lines = listOf(LyricLine(1000L, "test")),
        )

        assertEquals(
            "Artist - Song.lrc",
            com.music.bitchord.download.LyricsSidecarStore.fileNameFor("Artist - Song.m4a", artifactLrc),
        )
        assertEquals(
            "Artist - Song.ttml",
            com.music.bitchord.download.LyricsSidecarStore.fileNameFor("Artist - Song.flac", artifactTtml),
        )
    }

    @Test
    fun `standard toLrc formats stamps with two decimal centiseconds and ASCII padding`() {
        val lines = listOf(
            LyricLine(0L, "first line"),
            LyricLine(61_234L, "second line"),
        )
        assertEquals("[00:00.00]first line\n[01:01.23]second line", lines.toLrc())
    }

    @Test
    fun `toLrc rounds down within the centisecond`() {
        val lines = listOf(LyricLine(59_999L, "x"))
        assertEquals("[00:59.99]x", lines.toLrc())
    }
}
