package com.music.bitchord.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class StandbyQueueTransferTest {

    private fun player(items: List<MediaItem>): Player =
        Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getMediaItemCount" -> items.size
                "getMediaItemAt" -> items[args[0] as Int]
                else -> null
            }
        } as Player

    private fun item(id: String, entry: String? = null): MediaItem {
        val base = MediaItem.Builder().setMediaId(id).build()
        return if (entry == null) base else QueueShuffle.withQueueMetadata(base, newEntryId = entry)
    }

    @Test
    fun identicalQueuesCanBeReused() {
        val items = listOf(item("a", "e1"), item("b", "e2"), item("c", "e3"))
        assertTrue(StandbyQueueTransfer.canReuse(player(items), player(items)))
    }

    @Test
    fun emptyQueueIsNotReused() {
        assertFalse(StandbyQueueTransfer.canReuse(player(emptyList()), player(emptyList())))
    }

    @Test
    fun reorderedQueueIsNotReused() {
        val a = listOf(item("a", "e1"), item("b", "e2"))
        val b = listOf(item("b", "e2"), item("a", "e1"))
        assertFalse(StandbyQueueTransfer.canReuse(player(a), player(b)))
    }

    @Test
    fun removedItemIsNotReused() {
        val a = listOf(item("a", "e1"), item("b", "e2"))
        val b = listOf(item("a", "e1"))
        assertFalse(StandbyQueueTransfer.canReuse(player(a), player(b)))
    }

    @Test
    fun duplicateMediaWithDifferentEntryIdentityIsNotReused() {
        val a = listOf(item("same", "e1"), item("same", "e2"))
        val b = listOf(item("same", "e2"), item("same", "e1"))
        assertFalse(StandbyQueueTransfer.canReuse(player(a), player(b)))
    }
}
