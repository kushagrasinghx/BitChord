package com.music.bitchord.desktop

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Rich Presence framing.
 *
 * Discord answers a frame it cannot parse by closing the connection and saying
 * nothing, so a byte order or a length taken from the wrong string encoding is
 * indistinguishable from Discord not being there at all.
 */
class DesktopDiscordIpcTest {

    @Test
    fun aFrameIsOpcodeThenLengthThenBody() {
        val frame = discordFrame(1, """{"a":1}""")
        val header = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, header.int)
        assertEquals(7, header.int)
        assertEquals("""{"a":1}""", String(frame, 8, 7, Charsets.UTF_8))
    }

    @Test
    fun theLengthIsBytesRatherThanCharacters() {
        // A title with an accent in it is longer on the wire than on screen,
        // and a length taken from the string would truncate the JSON.
        val payload = """{"t":"Découverte"}"""
        val frame = discordFrame(1, payload)
        val declared = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN).also { it.int }.int
        assertEquals(payload.toByteArray(Charsets.UTF_8).size, declared)
        assertTrue(declared > payload.length)
        assertEquals(8 + declared, frame.size)
    }

    @Test
    fun theHandshakeCarriesOpcodeZero() {
        assertEquals(0, ByteBuffer.wrap(discordFrame(0, "{}")).order(ByteOrder.LITTLE_ENDIAN).int)
    }

    @Test
    fun anActivityCarriesTheFieldsDiscordDraws() {
        val activity = buildJsonObject {
            put("type", 2)
            put("details", "A song")
            put("state", "An artist")
        }
        val json = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            activity,
        )
        assertTrue("\"type\":2" in json, json)
        assertTrue("\"details\":\"A song\"" in json, json)
        assertTrue("\"state\":\"An artist\"" in json, json)
    }
}
