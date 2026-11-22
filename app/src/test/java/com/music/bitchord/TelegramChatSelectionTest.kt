package com.music.bitchord

import com.music.bitchord.data.telegram.TelegramChat
import com.music.bitchord.data.telegram.toTelegramChat
import com.music.bitchord.data.telegram.updateTelegramSelection
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramChatSelectionTest {
    @Test
    fun `TDLib channel title and type are retained`() {
        val chat = TdApi.Chat().apply {
            id = 73L
            title = "  HiFi Library  "
            type = TdApi.ChatTypeSupergroup(19L, true)
        }.toTelegramChat()

        assertEquals("HiFi Library", chat?.title)
        assertEquals("HiFi Library", chat?.displayTitle)
        assertEquals("Channel", chat?.typeLabel)
        assertTrue(chat?.isChannel == true)
    }

    @Test
    fun `blank update preserves known title and visible title never falls back to generic type`() {
        val previous = TelegramChat(73L, "My private group", false, selected = true)
        val update = TdApi.Chat().apply {
            id = 73L
            title = " "
            type = TdApi.ChatTypeBasicGroup(19L)
        }.toTelegramChat(previous)

        assertEquals("My private group", update?.displayTitle)
        assertTrue(update?.selected == true)
        assertEquals("Group", update?.typeLabel)
        assertFalse(update?.displayTitle == update?.typeLabel)
    }

    @Test
    fun `unsupported Telegram chat types are excluded`() {
        val chat = TdApi.Chat().apply {
            id = 4L
            title = "Saved Messages"
            type = TdApi.ChatTypePrivate(8L)
        }.toTelegramChat()

        assertNull(chat)
    }

    @Test
    fun `selection updates are idempotent and preserve other chats`() {
        val initial = setOf(10L, 20L)
        val selectedAgain = updateTelegramSelection(initial, 10L, true)
        val deselected = updateTelegramSelection(selectedAgain, 10L, false)

        assertEquals(initial, selectedAgain)
        assertEquals(setOf(20L), deselected)
        assertEquals(setOf(10L, 20L, 30L), updateTelegramSelection(initial, 30L, true))
    }
}
