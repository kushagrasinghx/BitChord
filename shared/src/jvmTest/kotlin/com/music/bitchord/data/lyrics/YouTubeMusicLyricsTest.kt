package com.music.bitchord.data.lyrics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The lyrics tab answers two different payloads depending on the client it is
 * asked as: the web client gets a plain description shelf, the phone gets
 * `timedLyricsModel`. Both shapes below are captured from the real endpoint.
 */
class YouTubeMusicLyricsTest {

    private fun page(json: String) = Json.parseToJsonElement(json).jsonObject

    /** `browse` as `ANDROID_MUSIC 7.21.50`, trimmed to two lines. */
    private val timed = page(
        """
        {
          "contents": {
            "elementRenderer": {
              "newElement": {
                "type": {
                  "componentType": {
                    "model": {
                      "timedLyricsModel": {
                        "lyricsData": {
                          "timedLyricsData": [
                            {
                              "lyricLine": "經過沿海的公路 越過漫漫河流",
                              "cueRange": {
                                "startTimeMilliseconds": "2210",
                                "endTimeMilliseconds": "7440",
                                "metadata": { "id": "0" }
                              }
                            },
                            {
                              "lyricLine": "second line",
                              "cueRange": {
                                "startTimeMilliseconds": "7440",
                                "endTimeMilliseconds": "9000",
                                "metadata": { "id": "1" }
                              }
                            }
                          ]
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
        """.trimIndent(),
    )

    /** The same page as `WEB_REMIX`, which never carries timings. */
    private val plain = page(
        """
        {
          "contents": {
            "sectionListRenderer": {
              "contents": [
                {
                  "musicDescriptionShelfRenderer": {
                    "description": {
                      "runs": [
                        { "text": "first line\nsecond line" }
                      ]
                    }
                  }
                }
              ]
            }
          }
        }
        """.trimIndent(),
    )

    @Test
    fun `reads the timed lyrics the mobile client is served`() {
        val lines = timedLyrics(timed)!!
        assertEquals(listOf("經過沿海的公路 越過漫漫河流", "second line"), lines.map { it.text })
        assertEquals(2_210L, lines[0].timeMs)
        assertEquals(7_440L, lines[0].sungUntilMs)
        assertEquals(9_000L, lines[1].sungUntilMs)
        // Line-level only: YouTube Music does not publish word timings here.
        assertFalse(lines[0].isWordSynced)
        assertTrue(lines.all { it.timeMs > 0 })
    }

    @Test
    fun `reads numeric timestamps as well as strings`() {
        val numeric = page(
            """
            {"timedLyricsData":[
              {"lyricLine":"line","cueRange":{"startTimeMilliseconds":1200,"endTimeMilliseconds":3400}}
            ]}
            """.trimIndent(),
        )
        val line = timedLyrics(numeric)!!.single()
        assertEquals(1_200L, line.timeMs)
        assertEquals(3_400L, line.sungUntilMs)
    }

    @Test
    fun `falls back to the plain shelf when the track has no timed lyrics`() {
        assertNull(timedLyrics(plain))
        val lines = plainLyrics(plain)!!
        assertEquals(listOf("first line", "second line"), lines.map { it.text })
        assertTrue(lines.all { it.timeMs == 0L })
    }

    @Test
    fun `a page with neither shape is not lyrics`() {
        assertNull(timedLyrics(page("""{"contents":{}}""")))
        assertNull(plainLyrics(page("""{"contents":{}}""")))
    }

    @Test
    fun `drops an empty line rather than leaving a gap in the timing`() {
        val withBlank = page(
            """
            {"timedLyricsData":[
              {"lyricLine":"  ","cueRange":{"startTimeMilliseconds":"0","endTimeMilliseconds":"100"}},
              {"lyricLine":"kept","cueRange":{"startTimeMilliseconds":"100","endTimeMilliseconds":"900"}}
            ]}
            """.trimIndent(),
        )
        val lines = timedLyrics(withBlank)!!
        assertEquals(listOf("kept"), lines.map { it.text })
        assertEquals(100L, lines.single().timeMs)
    }

    @Test
    fun timedLinesComeBackInPlaybackOrder() {
        val lines = timedLyrics(
            page(
                """
                {"timedLyricsData": [
                  {"lyricLine": "later", "cueRange": {"startTimeMilliseconds": "5000"}},
                  {"lyricLine": "sooner", "cueRange": {"startTimeMilliseconds": "1000"}}
                ]}
                """.trimIndent(),
            ),
        )
        assertEquals(listOf("sooner", "later"), lines?.map { it.text })
    }
}
