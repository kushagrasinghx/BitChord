package com.music.bitchord

import com.music.bitchord.data.lyrics.EnhancedLrc
import com.music.bitchord.data.lyrics.KaraokeLrc
import com.music.bitchord.data.lyrics.LrcLib
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricSyllable
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.ProviderLyrics
import com.music.bitchord.data.lyrics.PaxSenix
import com.music.bitchord.data.lyrics.normalizePaxSenixApiKey
import com.music.bitchord.data.lyrics.toEnhancedLrc
import com.music.bitchord.data.lyrics.youtubeStrings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLyricsTest {

    @Test
    fun `the imported providers are exposed`() {
        assertEquals(17, LyricsSource.entries.size)
        assertTrue(LyricsSource.offered.containsAll(listOf(
            LyricsSource.BETTER_LYRICS_PORTATO,
            LyricsSource.PAXSENIX_SPOTIFY,
            LyricsSource.PAXSENIX_MUSIXMATCH,
            LyricsSource.YOUTUBE_TRANSCRIPT,
            LyricsSource.YOUTUBE_MUSIC,
        )))
    }

    @Test
    fun `hidden providers are kept but never offered`() {
        assertTrue(LyricsSource.entries.containsAll(listOf(LyricsSource.SIMP_MUSIC, LyricsSource.MEGALOBIZ)))
        assertTrue(LyricsSource.offered.none { it.hidden })
        assertEquals(LyricsSource.LRC_RED, LyricsSource.offered.first())
    }

    @Test
    fun `a saved order drops hidden sources and puts a new first source on top`() {
        // What a previous build saved: every source it had, LRCLIB dragged to
        // the top, and the two now-hidden ones still in it.
        val saved = listOf(LyricsSource.LRCLIB) +
            (LyricsSource.entries - LyricsSource.LRC_RED - LyricsSource.LRCLIB)
        val ordered = LyricsSource.ordered(saved)
        assertEquals(
            listOf(LyricsSource.LRC_RED, LyricsSource.LRCLIB, LyricsSource.BINI_LYRICS),
            ordered.take(3),
        )
        assertEquals(LyricsSource.offered.toSet(), ordered.toSet())
        assertEquals(ordered.size, ordered.distinct().size)
    }

    @Test
    fun `a new source lands right after its declared neighbour`() {
        val saved = LyricsSource.offered - LyricsSource.BETTER_LYRICS_PORTATO
        assertEquals(LyricsSource.offered, LyricsSource.ordered(saved))
    }

    @Test
    fun `json wrapped TTML keeps Apple word timing`() {
        val raw = """{"content":"<tt><body><div><p begin=\"1.0\" end=\"2.0\"><span begin=\"1.0\" end=\"1.4\">sing </span><span begin=\"1.4\" end=\"2.0\">along</span></p></div></body></tt>"}"""
        val line = ProviderLyrics.parse(raw)!!.single { !it.isGap }
        assertEquals("sing along", line.text)
        assertEquals(2, line.words.size)
        assertEquals(2_000L, line.words.last().endMs)
    }

    @Test
    fun `NetEase prefix YRC gets Apple style word animation`() {
        val line = KaraokeLrc.parse("[1000,900](1000,400,0)sing(1400,500,0)along")
            .single { !it.isGap }
        assertEquals("singalong", line.text)
        assertEquals(listOf(1_000L, 1_400L), line.words.map { it.startMs })
        assertEquals(1_900L, line.words.last().endMs)
    }

    @Test
    fun `QQ suffix QRC gets Apple style word animation`() {
        val line = KaraokeLrc.parse("[1000,900]sing (1000,400,0)along(1400,500,0)")
            .single { !it.isGap }
        assertEquals("sing along", line.text)
        assertEquals(2, line.words.size)
        assertTrue(line.isWordSynced)
    }

    @Test
    fun `QQ XML envelope is recognized as karaoke rather than TTML`() {
        val qrc = """<QrcInfos><LyricInfo LyricContent="[1000,900]sing (1000,400,0)along(1400,500,0)"/></QrcInfos>"""
        val line = ProviderLyrics.parse(qrc)!!.single { !it.isGap }
        assertEquals("sing along", line.text)
        assertEquals(2, line.words.size)
    }

    @Test
    fun `structured PaxSenix Apple response keeps word timing`() {
        val raw = """
            {"type":"Word","content":[
              {"timestamp":1000,"text":[{"text":"sing","timestamp":1000},{"text":"along","timestamp":1400}]},
              {"timestamp":2200,"text":[{"text":"again","timestamp":2200}]}
            ]}
        """.trimIndent()
        val lines = PaxSenix.parseTimedApple(raw)!!.filterNot { it.isGap }
        assertEquals("sing along", lines.first().text)
        assertEquals(1_400L, lines.first().words[1].startMs)
        assertEquals(2_200L, lines.first().words[1].endMs)
    }

    @Test
    fun `PaxSenix accepts bare and bearer-prefixed API keys`() {
        assertEquals("secret", normalizePaxSenixApiKey(" secret "))
        assertEquals("secret", normalizePaxSenixApiKey("Bearer secret"))
        assertEquals("secret", normalizePaxSenixApiKey("bearer   secret "))
    }

    @Test
    fun `PaxSenix general fallback selects one candidate instead of repeating all`() {
        val raw = """
            {"lyrics":[
              {"id":"wrong","trackName":"Out of Touch","artistName":"Other","duration":201,
               "syncedLyrics":"[00:01.00]wrong line\n[00:02.00]wrong again"},
              {"id":"right","trackName":"Out of Time","artistName":"The Weeknd","duration":201,
               "syncedLyrics":"[00:01.00]first line\n[00:02.00]second line"},
              {"id":"duplicate","trackName":"Out of Time (Remix)","artistName":"The Weeknd","duration":240,
               "syncedLyrics":"[00:01.00]first line\n[00:02.00]second line"}
            ]}
        """.trimIndent()

        val lines = PaxSenix.parseLrcGet(raw, "Out of Time", "The Weeknd", 201_000L)!!
            .filterNot { it.isGap }
        assertEquals(listOf("first line", "second line"), lines.map { it.text })
        assertEquals(listOf(1_000L, 2_000L), lines.map { it.timeMs })
    }

    @Test
    fun `PaxSenix general fallback treats string array entries as separate LRC documents`() {
        val raw = """
            {"lyrics":[
              "[00:01.00]wrong first\n[00:40.00]wrong last",
              "[00:01.00]right first\n[03:19.00]right last",
              "[00:01.00]right first\n[03:19.00]right last"
            ]}
        """.trimIndent()

        val lines = PaxSenix.parseLrcGet(raw, "Song", "Artist", 200_000L)!!
            .filterNot { it.isGap }
        assertEquals(listOf("right first", "right last"), lines.map { it.text })
        assertEquals(listOf(1_000L, 199_000L), lines.map { it.timeMs })
    }

    @Test
    fun `YouTube nested text objects are traversed without crashing`() {
        val response = Json.parseToJsonElement(
            """{"text":{"runs":[{"text":"Lyrics"}]}}"""
        )

        assertEquals(listOf("Lyrics"), response.youtubeStrings())
    }

    // ---- Syllables, provider by provider -------------------------------------

    private val enoughSyllables = listOf(LyricSyllable(31_839, 31_996, 0, 1), LyricSyllable(31_996, 32_529, 1, 6))

    @Test
    fun `YRC glues syllables written without a space into one word`() {
        val line = KaraokeLrc.parse("[31000,1600](31000,839,0)long (31839,157,0)e(31996,533,0)nough")
            .single { !it.isGap }
        assertEquals("long enough", line.text)
        assertEquals(listOf("long", "enough"), line.words.map { it.text })
        assertEquals(enoughSyllables, line.words[1].syllables)
    }

    @Test
    fun `QRC in a script without spaces keeps a word per stamp`() {
        val line = KaraokeLrc.parse("[1000,900]\u6211(1000,300,0)\u7231(1300,300,0)\u4F60(1600,300,0)")
            .single { !it.isGap }
        assertEquals(3, line.words.size)
        assertTrue(line.words.all { it.syllables.isEmpty() })
    }

    @Test
    fun `enhanced lrc glues syllables instead of spacing them apart`() {
        val line = EnhancedLrc.parse("[00:30.18]<00:30.189>long <00:31.839>e<00:31.996>nough<00:32.529>")
            .single { !it.isGap }
        // Joined with a space per stamp, this used to read "long e nough".
        assertEquals("long enough", line.text)
        assertEquals(enoughSyllables, line.words[1].syllables)
    }

    @Test
    fun `enhanced lrc with no spaces anywhere keeps a word per stamp`() {
        val line = EnhancedLrc.parse("[00:01.00]<00:01.00>I<00:01.20>been<00:01.50>tryna<00:02.00>")
            .single { !it.isGap }
        assertEquals(listOf("I", "been", "tryna"), line.words.map { it.text })
        assertEquals("I been tryna", line.text)
    }

    /** The shape the live API returns without `ttml=true`, trimmed. */
    @Test
    fun `PaxSenix part flag joins syllables into one word`() {
        val raw = """
            {"type":"Syllable","content":[{"timestamp":31498,"endtime":32529,"text":[
              {"text":"long","timestamp":31498,"endtime":31839,"part":false},
              {"text":"e","timestamp":31839,"endtime":31996,"part":true},
              {"text":"nough","timestamp":31996,"endtime":32529,"part":false}
            ]}]}
        """.trimIndent()
        val line = PaxSenix.parseTimedApple(raw)!!.single { !it.isGap }
        assertEquals("long enough", line.text)
        assertEquals(listOf("long", "enough"), line.words.map { it.text })
        assertEquals(31_839L, line.words[0].endMs)
        assertEquals(enoughSyllables, line.words[1].syllables)
    }

    @Test
    fun `downloaded lyrics keep their syllables`() {
        val lines = listOf(
            LyricLine(
                timeMs = 31_498,
                text = "long enough",
                words = listOf(
                    LyricWord(31_498, 31_839, "long"),
                    LyricWord(31_839, 32_529, "enough", enoughSyllables),
                ),
            ),
            LyricLine(
                timeMs = 40_000,
                text = "enough",
                words = listOf(LyricWord(40_000, 40_690, "enough", enoughSyllables.map {
                    it.copy(startMs = it.startMs + 8_161, endMs = it.endMs + 8_161)
                })),
            ),
        )
        val back = LrcLib.parseLrc(lines.toEnhancedLrc()).filterNot { it.isGap }
        assertEquals(listOf("long enough", "enough"), back.map { it.text })
        assertEquals(listOf("long", "enough"), back[0].words.map { it.text })
        // Stamps are written in centiseconds, so times come back to the 10 ms.
        assertEquals(
            listOf(LyricSyllable(31_830, 31_990, 0, 1), LyricSyllable(31_990, 32_520, 1, 6)),
            back[0].words[1].syllables,
        )
        // One split word alone on its line still comes back as one word.
        assertEquals(listOf("enough"), back[1].words.map { it.text })
        assertEquals(2, back[1].words[0].syllables.size)
    }
}
