package com.music.bitchord.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/** The account's own listening history, which arrives grouped into dated shelves. */
class DesktopHistoryFeedTest {

    @Test
    fun everyDatedShelfContributesItsPlays() {
        val songs = DesktopSearchClient.parseBrowseSongs(historyFeed(), null)

        assertEquals(listOf("Shayar", "Deewana", "Faasle"), songs.map { it.title })
        assertEquals("Prabhakar Raj", songs.first().artist)
    }

    @Test
    fun aTrackPlayedOnMoreThanOneDayIsListedOnce() {
        val songs = DesktopSearchClient.parseBrowseSongs(historyFeed(repeatFirstRow = true), null)

        assertEquals(1, songs.count { it.videoId == "v1" })
    }

    private fun row(id: String, title: String, artist: String) = """
        {"musicResponsiveListItemRenderer":{
          "playlistItemData":{"videoId":"$id"},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title",
              "navigationEndpoint":{"watchEndpoint":{"videoId":"$id"}}}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$artist"}]}}}
          ]}}
    """.trimIndent()

    private fun historyFeed(repeatFirstRow: Boolean = false): JsonObject {
        val today = listOf(row("v1", "Shayar", "Prabhakar Raj"), row("v2", "Deewana", "Ajey Kr"))
        val yesterday = buildList {
            if (repeatFirstRow) add(row("v1", "Shayar", "Prabhakar Raj"))
            add(row("v3", "Faasle", "farhxn1080p"))
        }
        return Json.parseToJsonElement(
            """
            {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
              "sectionListRenderer":{"contents":[
                {"musicShelfRenderer":{"title":{"runs":[{"text":"Today"}]},
                  "contents":[${today.joinToString(",")}]}},
                {"musicShelfRenderer":{"title":{"runs":[{"text":"Yesterday"}]},
                  "contents":[${yesterday.joinToString(",")}]}}
              ]}}}}]}}}
            """.trimIndent(),
        ) as JsonObject
    }
}
