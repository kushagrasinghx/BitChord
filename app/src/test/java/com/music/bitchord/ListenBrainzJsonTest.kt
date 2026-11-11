package com.music.bitchord

import com.music.bitchord.data.scrobbling.ListenBrainzManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ListenBrainzJsonTest {

    private fun roundTrip(value: String): String {
        val parsed = Json.parseToJsonElement("""{"track":"${ListenBrainzManager.escapeJson(value)}"}""").jsonObject
        return parsed.getValue("track").jsonPrimitive.content
    }

    @Test
    fun `control characters survive JSON escaping`() {
        val value = "line1\rline2\tTabbed\\path\"quoted\"\u0001"
        assertEquals(value, roundTrip(value))
    }

    @Test
    fun `newlines and unicode survive JSON escaping`() {
        val value = "First\nSecond — 日本語"
        assertEquals(value, roundTrip(value))
    }
}
