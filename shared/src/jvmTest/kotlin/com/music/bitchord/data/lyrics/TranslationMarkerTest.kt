package com.music.bitchord.data.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class TranslationMarkerTest {
    @Test
    fun aChineseAnswerSplitsBackIntoItsLines() {
        // Google's zh-CN answer, as returned: each marker closes on U+E000.
        val body = "我会永远爱你\n0001\n今晚你别哭\n0002\n[合唱]"
        assertEquals(listOf("我会永远爱你", "今晚你别哭", "[合唱]"), LyricsTranslation.splitMarked(body))
    }

    @Test
    fun anArabicAnswerSplitsBackIntoItsLines() {
        // Google's ar answer, as returned: both private-use characters are gone.
        val body = "سأحبك دائما\n0001\nلا تبكي الليلة\n0002\n[جوقة]"
        assertEquals(listOf("سأحبك دائما", "لا تبكي الليلة", "[جوقة]"), LyricsTranslation.splitMarked(body))
        // The same answer with the counters in Arabic-Indic digits.
        assertEquals(
            listOf("سأحبك دائما", "لا تبكي الليلة"),
            LyricsTranslation.splitMarked("سأحبك دائما\n\u0660\u0660\u0660\u0661\nلا تبكي الليلة"),
        )
    }

    @Test
    fun aFourDigitLyricIsNotTakenForABareMarker() {
        val body = "الحفلة\n0001\n1999\n0002\nلا أندم على شيء"
        assertEquals(listOf("الحفلة", "1999", "لا أندم على شيء"), LyricsTranslation.splitMarked(body))
    }

    @Test
    fun otherAnswersStillSplit() {
        val body = "Je t'aimerai toujours\n0001\nNe pleure pas ce soir"
        assertEquals(listOf("Je t'aimerai toujours", "Ne pleure pas ce soir"), LyricsTranslation.splitMarked(body))
    }
}
