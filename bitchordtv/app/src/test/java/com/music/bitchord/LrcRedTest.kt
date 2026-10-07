package com.music.bitchord

import com.music.bitchord.data.lyrics.LrcRed
import com.music.bitchord.data.lyrics.TtmlLyrics
import com.music.bitchord.data.lyrics.lyricsJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * lrc.red, against what its host actually returned on 2026-10-04 — search hits
 * trimmed to the first few, TTML trimmed to a couple of paragraphs.
 */
class LrcRedTest {

    /** `GET https://lrc.red/search.json?q=Bohemian Rhapsody Queen` */
    private val bohemianSearch = """
        {"hits":[
        {"isrc":"GBUM71100986","title":"Bohemian Rhapsody (Operatic Section / 2011 A Cappella Mix)",
         "artist":"Queen","album":"Queen 40: Limited Edition Collector's Box Set, Vol. 1","year":1975,
         "duration":64.969,"cover":"https://lrc.red/s/GBUM71100986.webp"},
        {"isrc":"GBUM71029604","title":"Bohemian Rhapsody (Remastered 2011)","artist":"Queen",
         "album":"A Night at the Opera","year":1975,"duration":356.52,
         "cover":"https://lrc.red/s/GBUM71029604.webp"},
        {"isrc":"GBCEG0100046","title":"Bohemian Rhapsody (Live)","artist":"Queen","album":"Live Killers",
         "year":1979,"duration":352.04,"cover":"https://lrc.red/s/GBCEG0100046.webp","color":"#d3714d"}
        ],"next":"p24"}
    """.trimIndent()

    /** `GET https://lrc.red/search.json?q=Tum Hi Ho Mithoon, Arijit Singh` */
    private val tumHiHoSearch = """
        {"hits":[
        {"isrc":"INS181303031","title":"Tum Hi Ho (From \"Aashiqui 2\")","artist":"Arijit Singh",
         "album":"Aashiqui 2","year":2013,"duration":261.974},
        {"isrc":"INS181303860","title":"Tum Hi Ho (Remix)","artist":"Arijit Singh","duration":237.338},
        {"isrc":"GB8KE2422340","title":"Tum Hi Ho","artist":"Imazee","duration":163.699}
        ],"next":null}
    """.trimIndent()

    private fun hits(json: String) = lyricsJson.decodeFromString<LrcRed.Response>(json).hits!!

    @Test
    fun `reads a search page`() {
        val response = lyricsJson.decodeFromString<LrcRed.Response>(bohemianSearch)
        assertEquals(3, response.hits!!.size)
        assertEquals("p24", response.next)
        assertEquals(356.52, response.hits!![1].duration!!, 0.0)
    }

    @Test
    fun `skips the a cappella mix the search ranks first`() {
        val hit = LrcRed.best(hits(bohemianSearch), "Bohemian Rhapsody", "Queen", 355_000)
        assertEquals("GBUM71029604", hit?.isrc)
    }

    @Test
    fun `a live cut close in length is still not the studio recording`() {
        val live = LrcRed.best(hits(bohemianSearch), "Bohemian Rhapsody (Live)", "Queen", 352_000)
        assertEquals("GBCEG0100046", live?.isrc)
        assertNull(LrcRed.best(hits(bohemianSearch), "Bohemian Rhapsody", "Queen", 352_000))
    }

    @Test
    fun `a soundtrack credit in brackets and a shared artist are enough`() {
        val hit = LrcRed.best(hits(tumHiHoSearch), "Tum Hi Ho", "Mithoon, Arijit Singh", 262_000)
        assertEquals("INS181303031", hit?.isrc)
    }

    @Test
    fun `the same title by somebody else is not a match`() {
        assertNull(LrcRed.best(hits(tumHiHoSearch), "Tum Hi Ho", "Imazee Fan", 163_000))
        assertNull(LrcRed.best(hits(tumHiHoSearch), "Tum Hi Ho", "Arijit Singh", 200_000))
    }

    @Test
    fun `only a well formed isrc becomes a path`() {
        assertEquals("https://lrc.red/s/GBUM71029604.ttml", LrcRed.documentUrl(" gbum71029604 "))
        assertNull(LrcRed.documentUrl("../search.json?q=x"))
        assertNull(LrcRed.documentUrl(""))
    }

    /** `GET https://lrc.red/s/USUG11904206.ttml`, one paragraph with its echo. */
    private val blindingLights = """
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:lrc="http://lrc.red/lyric-ttml-internal"
         xmlns:ttm="http://www.w3.org/ns/ttml#metadata" lrc:timing="Word" xml:lang="en"><head><metadata>
        <ttm:agent type="person" xml:id="v1"><ttm:name type="full">Vocal 1</ttm:name></ttm:agent>
        <sourceMetadata xmlns="http://lrc.red/lyric-ttml-internal"><translations/>
        <audio lyricOffset="1.115" role="spatial"/></sourceMetadata></metadata></head><body dur="3:20.046">
        <div begin="2:19.610" end="2:23.809" lrc:songPart="Verse"><p begin="2:19.610" end="2:23.809" lrc:key="L27" ttm:agent="v1"><span begin="2:19.610" end="2:19.899">I'm</span> <span begin="2:19.899" end="2:20.059">just</span> <span begin="2:20.059" end="2:20.353">calling</span> <span begin="2:20.353" end="2:20.726">back</span> <span begin="2:20.726" end="2:20.906">to</span> <span begin="2:20.906" end="2:21.228">let</span> <span begin="2:21.228" end="2:21.445">you</span> <span begin="2:21.445" end="2:22.261">know</span> <span ttm:role="x-bg"><span begin="2:21.720" end="2:22.154">(Back</span> <span begin="2:22.154" end="2:22.295">to</span> <span begin="2:22.295" end="2:22.706">let</span> <span begin="2:22.706" end="2:22.889">you</span> <span begin="2:22.889" end="2:23.809">know)</span></span></p></div>
        </body></tt>
    """.trimIndent()

    @Test
    fun `its ttml reads as word timed with the echo kept apart`() {
        val line = TtmlLyrics.parse(blindingLights).single { !it.isGap }
        assertEquals("I'm just calling back to let you know", line.text)
        assertTrue(line.isWordSynced)
        assertEquals(139_610L, line.timeMs)
        assertNotNull(line.background)
        assertEquals("(Back to let you know)", line.background!!.text)
    }
}
