package com.music.bitchord

import com.music.bitchord.data.sources.module.JsLiteral
import com.music.bitchord.data.sources.module.settingsContextArg
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Values handed to module JavaScript are quoted, not concatenated.
 *
 * Module functions are invoked by assembling source text, so an unescaped quote
 * in a search query closes the literal and everything after it is evaluated as
 * code. These tests pin the quoting: a literal must survive a round trip
 * unchanged and must contain no character that can end it early.
 */
class JsLiteralTest {

    private val hostile = listOf(
        "\"",
        "artist \"live\"",
        "back\\slash",
        "line\nbreak",
        "carriage\rreturn",
        "tab\there",
        "\");globalThis.pwned=1;//",
        "quote ' and backtick `",
        "line\u2028separator",
        "paragraph\u2029separator",
        "\u0000nul",
        "emoji 🎵 and ünïcode",
    )

    @Test
    fun `every hostile value round-trips through the literal`() {
        for (value in hostile) {
            val literal = JsLiteral.encode(value)
            assertEquals(
                "literal must decode back to the original value: $literal",
                value,
                Json.parseToJsonElement(literal).jsonPrimitive.content,
            )
        }
    }

    @Test
    fun `a literal cannot be terminated by its own contents`() {
        for (value in hostile) {
            val literal = JsLiteral.encode(value)
            assertTrue("must be quoted: $literal", literal.startsWith("\"") && literal.endsWith("\""))
            // Only the two quotes the encoder added may be unescaped.
            val inner = literal.substring(1, literal.length - 1)
            var index = 0
            var unescapedQuotes = 0
            while (index < inner.length) {
                if (inner[index] == '\\') {
                    index += 2
                    continue
                }
                if (inner[index] == '"') unescapedQuotes++
                index++
            }
            assertEquals("no raw quote may remain in $literal", 0, unescapedQuotes)
            assertFalse("line separators must be escaped: $literal", literal.contains('\u2028'))
            assertFalse("paragraph separators must be escaped: $literal", literal.contains('\u2029'))
            assertFalse("raw newlines must be escaped: $literal", literal.contains('\n'))
        }
    }

    @Test
    fun `module settings are quoted as JSON, not concatenated`() {
        val settings = mapOf(
            "token" to "abc\"def",
            "name with \" quote" to "value\\with\\slashes",
        )

        val context = settingsContextArg(settings)

        // Parseable as JSON is the property that matters: an object literal that
        // parses cannot have been broken out of by a quote in a key or value.
        val parsed = Json.parseToJsonElement(context)
        val values = parsed.jsonObject.getValue("settings").jsonObject
        assertEquals(
            "abc\"def",
            values.getValue("token").jsonObject.getValue("value").jsonPrimitive.content,
        )
        assertEquals(
            "value\\with\\slashes",
            values.getValue("name with \" quote").jsonObject.getValue("value").jsonPrimitive.content,
        )
    }
}
