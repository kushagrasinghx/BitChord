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
    fun otherAnswersStillSplit() {
        val body = "Je t'aimerai toujours\n0001\nNe pleure pas ce soir"
        assertEquals(listOf("Je t'aimerai toujours", "Ne pleure pas ce soir"), LyricsTranslation.splitMarked(body))
    }
}
