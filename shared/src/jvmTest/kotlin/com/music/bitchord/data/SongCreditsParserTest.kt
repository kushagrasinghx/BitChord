package com.music.bitchord.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class SongCreditsParserTest {
    @Test
    fun parsesCreditsFromResponsiveListItems() {
        val page = Json.parseToJsonElement(
            """
            {
              "contents": {
                "sectionListRenderer": {
                  "contents": [{
                    "musicShelfRenderer": {
                      "contents": [
                        {"musicResponsiveListItemRenderer":{"flexColumns":[
                          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Performed by"}]}}},
                          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Eminem"}]}}}
                        ]}},
                        {"musicResponsiveListItemRenderer":{"flexColumns":[
                          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Written by"}]}}},
                          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Jeff Bass, Luis Resto & Marshall Mathers"}]}}}
                        ]}},
                        {"musicResponsiveListItemRenderer":{"flexColumns":[
                          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Produced by"}]}}},
                          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Eminem"}]}}}
                        ]}}
                      ]
                    }
                  }]
                }
              }
            }
            """.trimIndent()
        )

        val credits = SongInfoRepository.parseCreditsPage(page)

        assertEquals(3, credits.size)
        assertEquals("Performed by", credits[0].role)
        assertEquals(listOf("Eminem"), credits[0].names)
        assertEquals("Written by", credits[1].role)
        assertEquals(listOf("Jeff Bass", "Luis Resto & Marshall Mathers"), credits[1].names)
        assertEquals("Produced by", credits[2].role)
    }

    @Test
    fun findsCreditsEndpointByPageType() {
        val next = Json.parseToJsonElement(
            """
            {
              "contents": {
                "singleColumnMusicWatchNextResultsRenderer": {
                  "tabbedRenderer": {
                    "watchNextTabbedResultsRenderer": {
                      "tabs": [{
                        "tabRenderer": {
                          "content": {
                            "musicQueueRenderer": {
                              "content": {
                                "playlistPanelRenderer": {
                                  "contents": [{
                                    "playlistPanelVideoRenderer": {
                                      "videoId": "abcdefghijk",
                                      "menu": {
                                        "menuRenderer": {
                                          "items": [
                                            {"menuNavigationItemRenderer":{"text":{"runs":[{"text":"Go to album"}]},"navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_x"}}}},
                                            {"menuNavigationItemRenderer":{"text":{"runs":[{"text":"View song credits"}]},"navigationEndpoint":{"browseEndpoint":{"browseId":"MPTC_x","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_TRACK_CREDITS"}}}}}}
                                          ]
                                        }
                                      }
                                    }
                                  }]
                                }
                              }
                            }
                          }
                        }
                      }]
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )

        val endpoint = SongInfoRepository.findCreditsEndpoint(next, "abcdefghijk")

        assertEquals("MPTC_x", (endpoint?.get("browseId") as? kotlinx.serialization.json.JsonPrimitive)?.content)
    }

    @Test
    fun parsesCreditsFromDismissableDialogSections() {
        val page = Json.parseToJsonElement(
            """
            {
              "title": "Song credits",
              "sections": [
                {"dismissableDialogContentSectionRenderer":{"title":{"runs":[{"text":"Performed by"}]},"subtitle":{"runs":[{"text":"Eminem"}]}}},
                {"dismissableDialogContentSectionRenderer":{"title":{"runs":[{"text":"Written by"}]},"subtitle":{"runs":[{"text":"Jeff Bass"},{"text":"\n"},{"text":"Luis Resto"},{"text":"\n"},{"text":"Marshall Mathers"}]}}},
                {"dismissableDialogContentSectionRenderer":{"title":{"runs":[{"text":"Produced by"}]},"subtitle":{"runs":[{"text":"Eminem"}]}}},
                {"dismissableDialogContentSectionRenderer":{"title":{"runs":[{"text":"Music metadata provided by"}]},"subtitle":{"runs":[{"text":"Eminem Catalog PS"}]}}}
              ]
            }
            """.trimIndent()
        )

        val credits = SongInfoRepository.parseCreditsPage(page)

        assertEquals(listOf("Performed by", "Written by", "Produced by", "Music metadata provided by"), credits.map { it.role })
        assertEquals(listOf("Jeff Bass", "Luis Resto", "Marshall Mathers"), credits[1].names)
        assertEquals(listOf("Eminem Catalog PS"), credits[3].names)
    }
}
