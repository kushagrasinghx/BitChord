package com.music.bitchord.desktop

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Which YouTube rendition gets played. */
class DesktopFormatRankTest {

    @Test
    fun `the best bitrate within budget wins, whatever the codec`() {
        val ranked = DesktopStreamClient.rankByQuality(
            listOf(
                format("audio/mp4; codecs=\"mp4a.40.2\"", 128_000),
                format("audio/webm; codecs=\"opus\"", 160_000),
                format("audio/mp4; codecs=\"mp4a.40.2\"", 64_000),
            ),
            maxKbps = Int.MAX_VALUE,
        )

        assertTrue(
            ranked.first()["mimeType"]!!.jsonPrimitive.content.contains("opus"),
            "AAC still beat a higher-bitrate Opus rendition",
        )
        assertEquals(listOf(160, 128, 64), ranked.map { kbps(it) })
    }

    @Test
    fun `over-budget renditions come last, cheapest first`() {
        val ranked = DesktopStreamClient.rankByQuality(
            listOf(
                format("audio/webm; codecs=\"opus\"", 320_000),
                format("audio/mp4; codecs=\"mp4a.40.2\"", 96_000),
                format("audio/webm; codecs=\"opus\"", 256_000),
                format("audio/mp4; codecs=\"mp4a.40.2\"", 128_000),
            ),
            maxKbps = 130,
        )

        // Within budget descending, then over budget ascending: a rung over the ceiling still beats
        // no audio, and the cheapest such rung is the least wrong.
        assertEquals(listOf(128, 96, 256, 320), ranked.map { kbps(it) })
    }

    @Test
    fun `an unciphered rendition wins a tie`() {
        val ranked = DesktopStreamClient.rankByQuality(
            listOf(
                format("audio/webm; codecs=\"opus\"", 160_000, ciphered = true),
                format("audio/webm; codecs=\"opus\"", 160_000),
            ),
            maxKbps = Int.MAX_VALUE,
        )

        // A ciphered format costs a signature solve before it can even be tried.
        assertTrue(ranked.first()["url"] != null, "a ciphered rendition was preferred")
    }

    private fun kbps(format: JsonObject): Int =
        (format["bitrate"]?.jsonPrimitive?.int ?: 0) / 1000

    private fun format(mime: String, bitrate: Int, ciphered: Boolean = false): JsonObject =
        buildJsonObject {
            put("mimeType", mime)
            put("bitrate", bitrate)
            if (ciphered) put("signatureCipher", "s=x") else put("url", "https://x")
        }
}
