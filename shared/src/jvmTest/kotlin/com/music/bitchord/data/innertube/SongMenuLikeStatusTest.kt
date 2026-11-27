package com.music.bitchord.data.innertube

import com.music.bitchord.data.model.LikeStatus
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The watch answer's thumbs state is read by [InnertubeParser.parseSongMenu]
 * two ways: the legacy `likeButtonRenderer` on the queue row, and the
 * ViewModel-era `likeStatusEntity` YouTube is migrating to. Responses that
 * drop the renderer made every track parse as unrated — the player's star
 * never showed a liked song as liked — so both shapes are pinned here.
 */
class SongMenuLikeStatusTest {

    private fun queueRow(videoId: String, inner: String = ""): String =
        """
        {
          "playlistPanelVideoRenderer": {
            "videoId": "$videoId",
            "title": { "runs": [ { "text": "A title" } ] }${if (inner.isNotEmpty()) ",$inner" else ""}
          }
        }
        """.trimIndent()

    private fun likeButton(status: String): String =
        """
        "menu": { "menuRenderer": { "items": [
          { "menuServiceItemRenderer": { "icon": { "iconType": "LIBRARY_ADD" },
              "serviceEndpoint": { "feedbackEndpoint": { "feedbackToken": "tok" } } } }
        ] } },
        "likeButton": { "likeButtonRenderer": { "likeStatus": "$status", "target": { "videoId": "x" } } }
        """.trimIndent()

    private fun likeStatusEntity(videoId: String, status: String, field: String = "likeStatus"): String {
        // Keys are url-encoded base64 protobufs embedding the video id, as
        // served: "\x12\x0b<videoId>…" — see the parser's likeStatusEntityOf.
        val proto = "\u0012\u000b$videoId >("
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(proto.toByteArray())
        val key = java.net.URLEncoder.encode(b64, Charsets.UTF_8)
        return """
        "playerOverlays": { "playerOverlayRenderer": { "videoActionBar": { "videoActionBarViewModel": {
          "buttons": [ { "buttonViewModel": { "segmentedLikeDislikeButtonViewModel": {
            "likeButtonViewModel": { "likeButtonViewModel": {
              "likeStatusEntity": { "key": "$key", "$field": "$status" }
            } }
          } } } ]
        } } } }
        """.trimIndent()
    }

    private fun parse(body: String, videoId: String = "abc12345678") =
        InnertubeParser.parseSongMenu(Json.parseToJsonElement(body), videoId)

    @Test
    fun legacyLikeButtonRendererStatesTheRating() {
        val body = """{ "contents": ${queueRow("abc12345678", likeButton("LIKE"))} }"""
        assertEquals(LikeStatus.LIKE, parse(body)?.likeStatus)
    }

    @Test
    fun viewModelEntityStatesTheRatingWithoutARenderer() {
        val row = queueRow("abc12345678")
        val entity = likeStatusEntity("abc12345678", "LIKE")
        val body = """{ "contents": $row, $entity }"""
        val menu = parse(body)
        assertEquals(LikeStatus.LIKE, menu?.likeStatus)
    }

    @Test
    fun entityReadsTheStatusFieldSpelledEitherWay() {
        val row = queueRow("abc12345678")
        val entity = likeStatusEntity("abc12345678", "DISLIKE", field = "status")
        val body = """{ "contents": $row, $entity }"""
        assertEquals(LikeStatus.DISLIKE, parse(body)?.likeStatus)
    }

    @Test
    fun entityKeyedForAnotherVideoIsNotRead() {
        val row = queueRow("abc12345678")
        val entity = likeStatusEntity("zzzzzzzzzzz", "LIKE")
        val body = """{ "contents": $row, $entity }"""
        assertNull(parse(body)?.likeStatus)
    }

    @Test
    fun rendererWinsOverTheEntity() {
        val row = queueRow("abc12345678", likeButton("DISLIKE"))
        val entity = likeStatusEntity("abc12345678", "LIKE")
        val body = """{ "contents": $row, $entity }"""
        assertEquals(LikeStatus.DISLIKE, parse(body)?.likeStatus)
    }

    @Test
    fun neitherShapeMeansUnstatedNotIndifferent() {
        val body = """{ "contents": ${queueRow("abc12345678")} }"""
        assertNull(parse(body)?.likeStatus)
    }
}
