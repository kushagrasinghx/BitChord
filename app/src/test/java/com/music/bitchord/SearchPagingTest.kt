package com.music.bitchord

import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPagingTest {

    @Test
    fun `YouTube explicit badge is carried onto the song`() {
        fun row(id: String, badge: String) = """
          {
            "musicResponsiveListItemRenderer": {
              "playlistItemData": { "videoId": "$id" },
              "flexColumns": [
                { "musicResponsiveListItemFlexColumnRenderer": {
                  "text": { "runs": [{ "text": "Starboy" }] }
                } },
                { "musicResponsiveListItemFlexColumnRenderer": {
                  "text": { "runs": [{ "text": "The Weeknd" }, { "text": " • " }, { "text": "3:50" }] }
                } }
              ],
              "badges": $badge
            }
          }
        """.trimIndent()
        val explicitBadge = """[{"musicInlineBadgeRenderer":{"icon":{"iconType":"MUSIC_EXPLICIT_BADGE"}}}]"""
        val json = """{"contents":[${row("explicit", explicitBadge)},${row("clean", "[]") }]}"""

        val songs = InnertubeParser.parseSearchSongs(Json.parseToJsonElement(json).jsonObject)

        assertTrue(songs.first { it.videoId == "explicit" }.isExplicit == true)
        // No badge is not proof of a clean edition: several YouTube renderer
        // shapes simply omit badges altogether.
        assertNull(songs.first { it.videoId == "clean" }.isExplicit)
    }

    @Test
    fun `search page keeps rows and next continuation`() {
        val json = """
        {
          "contents": {
            "sectionListRenderer": {
              "contents": [
                {
                  "musicShelfRenderer": {
                    "contents": [
                      {
                        "musicResponsiveListItemRenderer": {
                          "navigationEndpoint": {
                            "browseEndpoint": {
                              "browseId": "VLPL123",
                              "browseEndpointContextSupportedConfigs": {
                                "browseEndpointContextMusicConfig": {
                                  "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                                }
                              }
                            }
                          },
                          "flexColumns": [
                            {
                              "musicResponsiveListItemFlexColumnRenderer": {
                                "text": { "runs": [{ "text": "One hundred songs" }] }
                              }
                            },
                            {
                              "musicResponsiveListItemFlexColumnRenderer": {
                                "text": { "runs": [{ "text": "Playlist" }] }
                              }
                            }
                          ],
                          "thumbnail": {
                            "musicThumbnailRenderer": {
                              "thumbnail": { "thumbnails": [{ "url": "https://example.test/art.jpg" }] }
                            }
                          }
                        }
                      }
                    ]
                  }
                },
                {
                  "continuationItemRenderer": {
                    "continuationEndpoint": {
                      "continuationCommand": { "token": "SEARCH_MORE" }
                    }
                  }
                }
              ]
            }
          }
        }
        """.trimIndent()

        val page = InnertubeParser.parseSearchPage(Json.parseToJsonElement(json).jsonObject)

        assertEquals("SEARCH_MORE", page.continuation)
        assertEquals(1, page.rows.size)
        val item = (page.rows.single() as SearchResult.Browse).item
        assertEquals("VLPL123", item.browseId)
        assertEquals("One hundred songs", item.title)
        assertEquals(BrowseType.PLAYLIST, item.type)
    }

    @Test
    fun `search page deduplicates repeated rows before pagination`() {
        val row = """
        {
          "musicResponsiveListItemRenderer": {
            "navigationEndpoint": {
              "browseEndpoint": {
                "browseId": "VLPL123",
                "browseEndpointContextSupportedConfigs": {
                  "browseEndpointContextMusicConfig": {
                    "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                  }
                }
              }
            },
            "flexColumns": [
              {
                "musicResponsiveListItemFlexColumnRenderer": {
                  "text": { "runs": [{ "text": "Repeated playlist" }] }
                }
              }
            ]
          }
        }
        """.trimIndent()
        val json = """
        {
          "contents": [ $row, $row ],
          "continuations": [
            { "nextContinuationData": { "continuation": "NEXT_PAGE" } }
          ]
        }
        """.trimIndent()

        val page = InnertubeParser.parseSearchPage(Json.parseToJsonElement(json).jsonObject)

        assertEquals("NEXT_PAGE", page.continuation)
        assertEquals(1, page.rows.size)
        assertTrue(page.rows.single() is SearchResult.Browse)
    }

    @Test
    fun `playlist continuation rows are real tracks even without set video ids`() {
        val json = """
        {
          "continuationContents": {
            "musicPlaylistShelfContinuation": {
              "contents": [
                {
                  "musicResponsiveListItemRenderer": {
                    "playlistItemData": { "videoId": "song-2" },
                    "flexColumns": [
                      {
                        "musicResponsiveListItemFlexColumnRenderer": {
                          "text": { "runs": [{ "text": "Second page song" }] }
                        }
                      },
                      {
                        "musicResponsiveListItemFlexColumnRenderer": {
                          "text": { "runs": [{ "text": "Artist" }, { "text": " • " }, { "text": "3:21" }] }
                        }
                      }
                    ]
                  }
                }
              ],
              "continuations": [
                { "nextContinuationData": { "continuation": "PLAYLIST_MORE" } }
              ]
            }
          }
        }
        """.trimIndent()

        val page = InnertubeParser.parsePlaylistShelf(Json.parseToJsonElement(json).jsonObject)

        requireNotNull(page)
        assertEquals(listOf("song-2"), page.songs.map { it.videoId })
        assertEquals(emptyList<String>(), page.suggested.map { it.videoId })
        assertEquals("PLAYLIST_MORE", page.continuation)
    }

    @Test
    fun `library item page keeps saved cards and next continuation`() {
        val json = """
        {
          "contents": [
            {
              "musicTwoRowItemRenderer": {
                "title": { "runs": [{ "text": "Road songs" }] },
                "subtitle": { "runs": [{ "text": "Playlist" }] },
                "navigationEndpoint": { "browseEndpoint": { "browseId": "VLPLROAD" } },
                "thumbnailRenderer": {
                  "musicThumbnailRenderer": {
                    "thumbnail": { "thumbnails": [{ "url": "https://example.test/road.jpg" }] }
                  }
                }
              }
            }
          ],
          "continuations": [
            { "nextContinuationData": { "continuation": "LIBRARY_MORE" } }
          ]
        }
        """.trimIndent()

        val page = InnertubeParser.parseLibraryItemPage(Json.parseToJsonElement(json).jsonObject)

        assertEquals("LIBRARY_MORE", page.continuation)
        assertEquals(1, page.items.size)
        assertEquals("VLPLROAD", page.items.single().browseId)
        assertEquals("Road songs", page.items.single().title)
    }

    @Test
    fun `user playlists filter still excludes non editable auto playlists after paging`() {
        val playlists = InnertubeParser.parseUserPlaylists(
            listOf(
                ShelfItem("Road songs", "Playlist", null, null, "VLPLROAD"),
                ShelfItem("Liked Music", "Auto playlist", null, null, "VLLM"),
                ShelfItem("Album", "Album", null, null, "MPREb_album"),
            ),
        )

        assertEquals(listOf("PLROAD"), playlists.map { it.playlistId })
    }
}
