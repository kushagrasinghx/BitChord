package com.music.bitchord

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.retimedForTranslation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTranslationTest {

    private val source = LyricLine(
        timeMs = 1_000,
        text = "I will always love you",
        words = listOf(
            LyricWord(1_000, 1_250, "I"),
            LyricWord(1_250, 1_700, "will"),
            LyricWord(1_700, 3_500, "always"),
            LyricWord(3_500, 4_000, "love"),
            LyricWord(4_000, 5_000, "you"),
        ),
    )

    @Test
    fun `translated words stay inside the source timing envelope`() {
        val translated = source.retimedForTranslation("Siempre te amaré")

        assertEquals("Siempre te amaré", translated.text)
        assertEquals(1_000L, translated.timeMs)
        assertEquals(1_000L, translated.words.first().startMs)
        assertEquals(5_000L, translated.words.last().endMs)
        assertEquals(listOf("Siempre", "te", "amaré"), translated.words.map { it.text })
        assertTrue(translated.words.zipWithNext().all { (a, b) ->
            a.startMs <= a.endMs && a.endMs <= b.startMs
        })
    }

    @Test
    fun `translated sweep retains the held middle of the source line`() {
        val translated = source.retimedForTranslation("Siempre te amaré")

        // "always" owns most of the source line's middle, so the projected
        // translation has not already reached its final word at that point.
        assertTrue(translated.revealedChars(2_000) < translated.text.length)
        assertEquals(translated.text.length.toFloat(), translated.revealedChars(5_000))
    }

    @Test
    fun `line synced lyrics remain line synced after translation`() {
        val translated = LyricLine(
            timeMs = 8_000,
            text = "A plain line",
            sungUntilMs = 11_000,
        ).retimedForTranslation("Una línea sencilla")

        assertFalse(translated.isWordSynced)
        assertEquals(8_000L, translated.timeMs)
        assertEquals(11_000L, translated.sungUntilMs)
    }

    @Test
    fun `unspaced translations receive grapheme timing`() {
        val translated = source.retimedForTranslation("永远爱你")

        assertEquals("永远爱你", translated.words.joinToString("") { it.text })
        assertTrue(translated.words.size > 1)
        assertEquals(1_000L, translated.words.first().startMs)
        assertEquals(5_000L, translated.words.last().endMs)
    }

    @Test
    fun `background vocals keep their independent clock`() {
        val backing = LyricLine(
            timeMs = 2_500,
            text = "echo",
            words = listOf(LyricWord(2_500, 4_500, "echo")),
        )
        val translated = source.copy(background = backing).retimedForTranslation(
            translatedText = "Siempre te amaré",
            translatedBackground = backing.retimedForTranslation("eco"),
        )

        assertEquals("eco", translated.background?.text)
        assertEquals(2_500L, translated.background?.timeMs)
        assertEquals(4_500L, translated.background?.endMs)
        assertEquals(5_000L, translated.endMs)
    }
}
