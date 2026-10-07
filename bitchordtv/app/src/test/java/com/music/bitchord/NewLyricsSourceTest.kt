package com.music.bitchord

import com.music.bitchord.data.lyrics.BiniLyrics
import com.music.bitchord.data.lyrics.Unison
import com.music.bitchord.data.lyrics.artistForLyricsSearch
import com.music.bitchord.data.lyrics.forLyricsSearch
import com.music.bitchord.data.lyrics.lyricsJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two sources added alongside ISRC matching, tested against the responses
 * their hosts actually returned rather than against what their docs claim.
 *
 * Both are third-party services with no published schema, so the payloads
 * below are captured verbatim — trimmed only where a field is long enough to
 * be unreadable. A field renamed upstream should fail here, not in the player.
 */
class NewLyricsSourceTest {

    // ---- BiniLyrics ---------------------------------------------------------

    /** `GET https://lyrics-api.binimum.org/?track=golden%20hour&artist=JVKE` */
    private val biniSearch = """
        {"total":1,"source":"HIT-EXACT","results":[{"id":"69b0336e00361778ec55",
        "track_name":"golden hour","artist_name":"JVKE",
        "album_name":"golden hour - Single","duration":209,"isrc":"GBKPL2204171",
        "timing_type":"word",
        "lyricsUrl":"https://lyrics-storage.binimum.org/GBKPL2204171.ttml"}]}
    """.trimIndent().replace("\n", "")

    @Test
    fun `reads a binilyrics search hit`() {
        val response = lyricsJson.decodeFromString<BiniLyrics.Response>(biniSearch)
        val hit = response.results!!.single()
        assertEquals("HIT-EXACT", response.source)
        assertEquals("GBKPL2204171", hit.isrc)
        assertEquals("word", hit.timingType)
        assertEquals("golden hour", hit.trackName)
        assertEquals(209, hit.duration)
        assertEquals("https://lyrics-storage.binimum.org/GBKPL2204171.ttml", hit.lyricsUrl)
    }

    @Test
    fun `a binilyrics response with no results is not a match`() {
        val response = lyricsJson.decodeFromString<BiniLyrics.Response>("""{"total":0}""")
        assertNull(response.results?.firstOrNull())
    }

    // ---- Unison -------------------------------------------------------------

    /**
     * `GET https://unison.boidu.dev/lyrics?song=golden%20hour&artist=JVKE`.
     * The submitter block is kept: it is the part most likely to change shape,
     * and decoding has to survive it doing so.
     */
    private val unisonLrc = """
        {"success":true,"data":{"id":4883,"videoId":"uV_5eEvamoQ",
        "song":"golden hour","artist":"JVKE","album":"this is what ____ feels like",
        "lyrics":"[00:15.46]It was just two lovers\n[00:17.30]Sittin' in the car",
        "format":"lrc","language":"en","syncType":"linesync","score":0,
        "effectiveScore":0,"voteCount":0,"confidence":"low","hidden":false,
        "submitter":{"keyId":"dea04ab0","reputation":2,"displayName":"Prime",
        "tier":null,"level":1,"badgeCount":2,
        "topBadge":{"key":"early-adopter","name":"Early Adopter"}},"userVote":null}}
    """.trimIndent().replace("\n", "")

    @Test
    fun `reads a line-synced unison entry`() {
        val response = lyricsJson.decodeFromString<Unison.Response>(unisonLrc)
        assertEquals(true, response.success)
        // The intro before the first stamp comes back as an instrumental gap,
        // same as every other LRC source here.
        val lines = Unison.linesOf(response.data!!)!!.filterNot { it.isGap }
        assertEquals(listOf("It was just two lovers", "Sittin' in the car"), lines.map { it.text })
        assertEquals(15_460L, lines[0].timeMs)
        assertEquals(17_300L, lines[1].timeMs)
        // Line-synced: stamps, but no word timings to sweep along.
        assertTrue(lines.none { it.isWordSynced })
    }

    @Test
    fun `reads a word-synced unison entry as ttml`() {
        val lines = Unison.linesOf(
            Unison.Entry(
                format = "ttml",
                syncType = "wordsync",
                lyrics = """
                    <tt><body><div><p begin="1.0" end="2.0">
                      <span begin="1.0" end="1.4">hold</span>
                      <span begin="1.4" end="2.0">on</span>
                    </p></div></body></tt>
                """.trimIndent(),
            ),
        )!!
        val line = lines.single { !it.isGap }
        assertEquals("hold on", line.text)
        assertTrue(line.isWordSynced)
        assertEquals(1_400L, line.words[0].endMs)
    }

    @Test
    fun `reads an unsynced unison entry as plain lines`() {
        val lines = Unison.linesOf(
            Unison.Entry(
                format = "lrc",
                syncType = "plain",
                lyrics = "  first line \n\n second line \n",
            ),
        )!!
        assertEquals(listOf("first line", "second line"), lines.map { it.text })
        // All at zero, which is how the panel is told there is nothing to follow.
        assertTrue(lines.all { it.timeMs == 0L })
    }

    @Test
    fun `an entry with no lyrics in it is not a match`() {
        assertNull(Unison.linesOf(Unison.Entry(format = "lrc", syncType = "linesync")))
        assertNull(Unison.linesOf(Unison.Entry(format = "lrc", syncType = "linesync", lyrics = "  ")))
    }

    // ---- Turning a YouTube title into a searchable one -----------------------

    @Test
    fun `strips the credits a catalogue does not file a track under`() {
        // The one that sent this whole track to the wrong source.
        assertEquals("Dracula", "Dracula (feat. JENNIE)".forLyricsSearch())
        assertEquals("Dracula", "Dracula (with JENNIE)".forLyricsSearch())
        assertEquals("Dracula", "Dracula [ft. JENNIE]".forLyricsSearch())
        assertEquals("Dracula", "Dracula feat. JENNIE".forLyricsSearch())
        assertEquals("Dracula", "Dracula featuring JENNIE".forLyricsSearch())
    }

    @Test
    fun `strips how an upload was labelled`() {
        assertEquals("Blinding Lights", "Blinding Lights (Official Video)".forLyricsSearch())
        assertEquals("Blinding Lights", "Blinding Lights (Official Music Video)".forLyricsSearch())
        assertEquals("Blinding Lights", "Blinding Lights [Lyrics]".forLyricsSearch())
        assertEquals("Blinding Lights", "Blinding Lights (Visualizer)".forLyricsSearch())
        assertEquals("Blinding Lights", "Blinding Lights (4K)".forLyricsSearch())
    }

    @Test
    fun `leaves alone anything that names a different recording`() {
        // These look like the same kind of bracket and are the opposite: taking
        // them off searches for a recording other than the one playing, and
        // comes back with the wrong words confidently in time.
        for (title in listOf(
            "Dracula (JENNIE Remix)",
            "Everlong (Acoustic)",
            "Song 2 (Live)",
            "Bohemian Rhapsody (Remastered 2011)",
            "Nights (Sped Up)",
        )) {
            assertEquals(title, title.forLyricsSearch())
        }
    }

    @Test
    fun `handles both at once and leaves ordinary titles untouched`() {
        assertEquals("Levitating", "Levitating (feat. DaBaby) [Official Video]".forLyricsSearch())
        assertEquals("golden hour", "golden hour".forLyricsSearch())
        // A title that is nothing but packaging keeps what it had; better to
        // ask with a bad name than with none.
        assertEquals("(Official Video)", "(Official Video)".forLyricsSearch())
    }

    @Test
    fun `drops the topic suffix from an auto-generated channel`() {
        assertEquals("Tame Impala", "Tame Impala - Topic".artistForLyricsSearch())
        assertEquals("Tame Impala", "Tame Impala".artistForLyricsSearch())
    }
}
