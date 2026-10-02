package com.music.bitchord

import com.music.bitchord.data.innertube.InnertubeParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeRecommendationFeedbackParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `initial Home extracts REMOVE_CIRCLE action and its genuine inverse`() {
        val item = InnertubeParser.parseHome(home(twoRow(menu("REMOVE_CIRCLE", "Ne plus recommander cet artiste"))))
            .single().items.single()
        val action = item.dontRecommendArtist!!

        assertEquals("Ne plus recommander cet artiste", action.label)
        assertEquals("feedback-token-artist", action.forwardToken)
        assertEquals("feedback-token-artist-undo", action.undoToken)
        assertEquals("Nous adapterons vos recommandations", action.confirmationText)
        assertEquals("Annuler", action.undoLabel)
    }

    @Test
    fun `Home item without the action exposes nothing`() {
        val item = InnertubeParser.parseHome(home(twoRow(""))).single().items.single()
        assertNull(item.dontRecommendArtist)
    }

    @Test
    fun `multiple feedback actions select REMOVE_CIRCLE rather than unrelated feedback`() {
        val menus = menu("NOT_INTERESTED", "Pas intéressé", "feedback-token-other") + "," +
            menu("REMOVE_CIRCLE", "Ne plus recommander cet artiste")
        val action = InnertubeParser.parseHome(home(twoRow(menus)))
            .single().items.single().dontRecommendArtist!!
        assertEquals("feedback-token-artist", action.forwardToken)
    }

    @Test
    fun `NOT_INTERESTED alone does not expose artist feedback`() {
        val item = InnertubeParser.parseHome(
            home(twoRow(menu("NOT_INTERESTED", "Pas intéressé"))),
        ).single().items.single()
        assertNull(item.dontRecommendArtist)
    }

    @Test
    fun `same endpoint shape with another icon is not a false positive`() {
        val item = InnertubeParser.parseHome(home(twoRow(menu("DELETE", "Supprimer de l'historique"))))
            .single().items.single()
        assertNull(item.dontRecommendArtist)
    }

    @Test
    fun `library toggle feedback is ignored`() {
        val toggle = """{"toggleMenuServiceItemRenderer":{"defaultIcon":{"iconType":"LIBRARY_ADD"},"defaultServiceEndpoint":{"feedbackEndpoint":{"feedbackToken":"feedback-token-library"}},"toggledServiceEndpoint":{"feedbackEndpoint":{"feedbackToken":"feedback-token-library-undo"}}}}"""
        val item = InnertubeParser.parseHome(home(twoRow(toggle))).single().items.single()
        assertNull(item.dontRecommendArtist)
    }

    @Test
    fun `recommendation and history toggle shapes are ignored`() {
        val toggle = """{"toggleMenuServiceItemRenderer":{"defaultIcon":{"iconType":"REMOVE_CIRCLE"},"defaultServiceEndpoint":{"feedbackEndpoint":{"feedbackToken":"feedback-token-toggle"}},"toggledServiceEndpoint":{"feedbackEndpoint":{"feedbackToken":"feedback-token-toggle-undo"}}}}"""
        val item = InnertubeParser.parseHome(home(twoRow(toggle))).single().items.single()
        assertNull(item.dontRecommendArtist)
    }

    @Test
    fun `continuation preserves the same Home action`() {
        val root = obj("""{"continuationContents":{"musicShelfRenderer":${plainShelf(menu("REMOVE_CIRCLE", "別のおすすめ"))}}}""")
        val action = InnertubeParser.parseHomeContinuation(root)
            .single().items.single().dontRecommendArtist
        assertEquals("feedback-token-artist", action?.forwardToken)
        assertEquals("別のおすすめ", action?.label)
    }

    @Test
    fun `responsive renderer on first Home page preserves the action`() {
        val action = InnertubeParser.parseHome(
            home(responsive(menu("REMOVE_CIRCLE", "No volver a recomendar este artista"))),
        ).single().items.single().dontRecommendArtist
        assertEquals("feedback-token-artist", action?.forwardToken)
        assertEquals("No volver a recomendar este artista", action?.label)
    }

    @Test
    fun `missing forward token rejects otherwise matching action`() {
        val broken = menu("REMOVE_CIRCLE", "Ne plus recommander cet artiste")
            .replace("\"feedbackToken\":\"feedback-token-artist\",", "")
        val item = InnertubeParser.parseHome(home(twoRow(broken))).single().items.single()
        assertNull(item.dontRecommendArtist)
    }

    @Test
    fun `action stays attached only to the card that owns it`() {
        val response = home(
            twoRow("", title = "Sans action") + "," +
                twoRow(menu("REMOVE_CIRCLE", "Ne pas recommander l'artiste"), title = "Avec action"),
        )
        val items = InnertubeParser.parseHome(response).single().items
        assertNull(items[0].dontRecommendArtist)
        assertEquals("feedback-token-artist", items[1].dontRecommendArtist?.forwardToken)
    }

    @Test
    fun `each card keeps its own forward and inverse token pair`() {
        val response = home(
            twoRow(menu("REMOVE_CIRCLE", "Masquer A", "forward-a", "undo-a"), title = "A") + "," +
                twoRow(menu("REMOVE_CIRCLE", "Masquer B", "forward-b", "undo-b"), title = "B"),
        )
        val actions = InnertubeParser.parseHome(response).single().items.map { it.dontRecommendArtist!! }
        assertEquals(listOf("forward-a" to "undo-a", "forward-b" to "undo-b"), actions.map { it.forwardToken to it.undoToken })
    }

    private fun home(renderer: String): JsonObject = obj(
        """{"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Pour vous"}]}}},"contents":[$renderer]}}]}}}}]}}}""",
    )

    private fun twoRow(menuItems: String, title: String = "Artiste test") =
        """{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"$title"}]},"subtitle":{"runs":[{"text":"Artiste"}]},"navigationEndpoint":{"browseEndpoint":{"browseId":"UC-fake-$title"}},"menu":{"menuRenderer":{"items":[$menuItems]}}}}"""

    private fun plainShelf(menuItems: String) =
        """{"title":{"runs":[{"text":"Sélection"}]},"contents":[${responsive(menuItems)}]}"""

    private fun responsive(menuItems: String) =
        """{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"video-fake"},"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Titre"}]}}},{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Artiste"}]}}}],"menu":{"menuRenderer":{"items":[$menuItems]}}}}"""

    private fun menu(
        icon: String,
        label: String,
        token: String = "feedback-token-artist",
        undoToken: String = "feedback-token-artist-undo",
    ) =
        """{"menuServiceItemRenderer":{"text":{"runs":[{"text":"$label"}]},"icon":{"iconType":"$icon"},"serviceEndpoint":{"feedbackEndpoint":{"feedbackToken":"$token","actions":[{"hideEnclosingAction":{}},{"openPopupAction":{"popup":{"notificationActionRenderer":{"responseText":{"runs":[{"text":"Nous adapterons vos recommandations"}]},"actionButton":{"buttonRenderer":{"text":{"runs":[{"text":"Annuler"}]},"command":{"feedbackEndpoint":{"feedbackToken":"$undoToken"}}}}}}}}]}}}}"""

    private fun obj(value: String): JsonObject = json.parseToJsonElement(value) as JsonObject
}
