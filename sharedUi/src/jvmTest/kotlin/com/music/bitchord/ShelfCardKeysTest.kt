package com.music.bitchord

import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.ui.screens.shelfCardKeys
import org.junit.Assert.assertEquals
import org.junit.Test

/** A lazy row throws on a repeated key, so a shelf's cards each get one of their own. */
class ShelfCardKeysTest {

    private fun item(title: String, browseId: String? = null, videoId: String? = null) =
        ShelfItem(title = title, subtitle = "", thumbnailUrl = null, videoId = videoId, browseId = browseId)

    @Test
    fun `cards are keyed by browse id, then video id, then title`() {
        assertEquals(
            listOf("card:VL1", "card:abc", "card:Folder"),
            shelfCardKeys(listOf(item("Mix", browseId = "VL1"), item("Song", videoId = "abc"), item("Folder"))),
        )
    }

    @Test
    fun `a repeated card still gets a key of its own`() {
        val keys = shelfCardKeys(
            listOf(item("Mix", browseId = "VL1"), item("Mix", browseId = "VL1"), item("Hits"), item("Hits")),
        )
        assertEquals(listOf("card:VL1", "card:VL1#2", "card:Hits", "card:Hits#2"), keys)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `no card can take the leading or trailing card's key`() {
        assertEquals(listOf("card:leading", "card:trailing"), shelfCardKeys(listOf(item("leading"), item("trailing"))))
    }
}
