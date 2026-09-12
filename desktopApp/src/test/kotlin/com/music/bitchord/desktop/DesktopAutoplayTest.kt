package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.QueueBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Autoplay's two halves: reading a watch queue, and turning one into a station. */
class DesktopAutoplayTest {

    @Test
    fun `a watch queue parses into songs, keeping credits and spotting videos`() {
        val response = Json.parseToJsonElement(
            """
            {
              "contents": {
                "playlistPanelRenderer": {
                  "contents": [
                    {
                      "playlistPanelVideoRenderer": {
                        "videoId": "aaa",
                        "title": {
                          "runs": [
                            {
                              "text": "A Catalogue Track"
                            }
                          ]
                        },
                        "longBylineText": {
                          "runs": [
                            {
                              "text": "An Artist",
                              "navigationEndpoint": {
                                "browseEndpoint": {
                                  "browseId": "UCartist",
                                  "browseEndpointContextSupportedConfigs": {
                                    "browseEndpointContextMusicConfig": {
                                      "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                                    }
                                  }
                                }
                              }
                            },
                            {
                              "text": " • "
                            },
                            {
                              "text": "An Album",
                              "navigationEndpoint": {
                                "browseEndpoint": {
                                  "browseId": "MPREalbum",
                                  "browseEndpointContextSupportedConfigs": {
                                    "browseEndpointContextMusicConfig": {
                                      "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                                    }
                                  }
                                }
                              }
                            },
                            {
                              "text": " • "
                            },
                            {
                              "text": "2021"
                            }
                          ]
                        },
                        "lengthText": {
                          "runs": [
                            {
                              "text": "3:24"
                            }
                          ]
                        },
                        "thumbnail": {
                          "thumbnails": [
                            {
                              "url": "https://example/art.jpg",
                              "width": 544,
                              "height": 544
                            }
                          ]
                        }
                      }
                    },
                    {
                      "playlistPanelVideoRenderer": {
                        "videoId": "bbb",
                        "title": {
                          "runs": [
                            {
                              "text": "A Music Video"
                            }
                          ]
                        },
                        "longBylineText": {
                          "runs": [
                            {
                              "text": "Another Artist"
                            },
                            {
                              "text": " • "
                            },
                            {
                              "text": "417M views"
                            },
                            {
                              "text": " • "
                            },
                            {
                              "text": "2.4M likes"
                            }
                          ]
                        },
                        "lengthText": {
                          "runs": [
                            {
                              "text": "4:01"
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
        ) as JsonObject

        val songs = DesktopSearchClient.parseWatchQueue(response)

        assertEquals(listOf("aaa", "bbb"), songs.map { it.videoId })
        val first = songs[0]
        assertEquals("A Catalogue Track", first.title)
        // Only the runs before the first bullet are the credit; the album and the year are not part
        // of the artist's name.
        assertEquals("An Artist", first.artist)
        assertEquals("An Album", first.albumName)
        assertEquals("3:24", first.durationText)
        assertEquals("https://example/art.jpg", first.thumbnailUrl)
        assertTrue(!first.isVideo)
        // "417M views" in the byline is what distinguishes a music-video upload from the catalogue
        // cut of the same recording.
        assertTrue(songs[1].isVideo, "a video upload was not recognised")
    }

    @Test
    fun `a station drops what is queued and caps any one artist`() {
        val playing = Song("seed", "Seed Song", "Seed Artist", null)
        val candidates = listOf(
            // Already playing, under its music-video title — the case id equality misses and the
            // whole reason Autoplay felt broken.
            Song("other", "Seed Song (Official Video)", "Seed Artist", null),
            Song("s1", "Seed Two", "Seed Artist", null),
            Song("s2", "Seed Three", "Seed Artist", null),
            Song("s3", "Seed Four", "Seed Artist", null),
            Song("s4", "Seed Five", "Seed Artist", null),
            Song("s5", "Seed Six", "Seed Artist", null),
            Song("o1", "Other One", "Other Artist", null),
            Song("o2", "Other Two", "Other Artist", null),
            Song("o3", "Other Three", "Other Artist", null),
        )

        val station = QueueBuilder.extend(listOf(playing), candidates, limit = 10)

        assertTrue(station.none { it.videoId == "other" }, "the playing track came back as its own video")
        assertTrue(
            station.count { it.artist == "Seed Artist" } <= QueueBuilder.SEED_ARTIST_LIMIT,
            "the seed artist took over the station",
        )
        assertTrue(
            station.count { it.artist == "Other Artist" } <= QueueBuilder.PER_ARTIST_LIMIT,
            "one artist exceeded the per-artist cap",
        )
        assertTrue(station.isNotEmpty(), "no station was built at all")
    }
}
