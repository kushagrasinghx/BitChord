package com.music.bitchord.mcp

import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test

class McpUtilitiesTest {
    private fun validate(name: String, json: String) =
        McpUtilities.validate(name, Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject)

    @Test fun acceptsBoundaries() {
        validate("recommendations", "{\"limit\":50,\"video_id\":\"abcdefghijk\"}")
        validate("listening_summary", "{\"period\":\"this_week\",\"limit\":1}")
        validate("sleep_timer_set", "{\"minutes\":720,\"after_track\":false}")
        validate("sleep_timer_set", "{\"after_track\":true}")
        validate("sleep_timer_get", "{}")
    }

    @Test fun rejectsInvalidArgumentsBeforeWork() {
        val cases = listOf(
            "recommendations" to "{\"limit\":51}",
            "downloads_list" to "{\"limit\":0}",
            "downloads_list" to "{\"limit\":\"2\"}",
            "downloads_list" to "{\"limit\":2.0}",
            "download_enqueue" to "{\"video_id\":\"https://youtube.com/x\"}",
            "download_cancel" to "{}",
            "download_status" to "{\"video_id\":null}",
            "listening_summary" to "{\"period\":\"week\"}",
            "listening_summary" to "{}",
            "sleep_timer_set" to "{\"minutes\":721}",
            "sleep_timer_set" to "{\"minutes\":30,\"after_track\":true}",
            "sleep_timer_set" to "{\"after_track\":false}",
            "sleep_timer_set" to "{\"after_track\":\"true\"}",
            "sleep_timer_get" to "{\"extra\":true}",
        )
        cases.forEach { (name, args) ->
            assertThrows("$name $args", IllegalArgumentException::class.java) { validate(name, args) }
        }
    }
}
