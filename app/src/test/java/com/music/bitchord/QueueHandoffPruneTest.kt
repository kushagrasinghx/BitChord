package com.music.bitchord

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.QueueCoordinator
import com.music.bitchord.playback.toMediaItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class QueueHandoffPruneTest {

    private fun player(items: MutableList<MediaItem>, index: Int): Player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getCurrentMediaItemIndex" -> index
            "getMediaItemCount" -> items.size
            "getMediaItemAt" -> items[args[0] as Int]
            "getCurrentMediaItem" -> items.getOrNull(index)
            "removeMediaItems" -> {
                val from = args[0] as Int
                val to = args[1] as Int
                for (i in (to - 1) downTo from) items.removeAt(i)
                null
            }
            else -> null
        }
    } as Player

    @Test
    fun `blended handoff into context prunes consumed user queue rows`() {
        val items = mutableListOf(
            Song(videoId = "context-a", title = "A", artist = "x", thumbnailUrl = null, queueTier = QueueTier.CONTEXT).toMediaItem(),
            Song(videoId = "user-b", title = "B", artist = "x", thumbnailUrl = null, queueTier = QueueTier.USER_QUEUE).toMediaItem(),
            Song(videoId = "context-c", title = "C", artist = "x", thumbnailUrl = null, queueTier = QueueTier.CONTEXT).toMediaItem(),
        )
        QueueCoordinator.consumePlayedUserQueue(player(items, index = 2))
        assertEquals(listOf("context-a", "context-c"), items.map { it.mediaId })
    }

    @Test
    fun `handoff into a user queue row keeps later user rows`() {
        val items = mutableListOf(
            Song(videoId = "context-a", title = "A", artist = "x", thumbnailUrl = null, queueTier = QueueTier.CONTEXT).toMediaItem(),
            Song(videoId = "user-b", title = "B", artist = "x", thumbnailUrl = null, queueTier = QueueTier.USER_QUEUE).toMediaItem(),
            Song(videoId = "user-c", title = "C", artist = "x", thumbnailUrl = null, queueTier = QueueTier.USER_QUEUE).toMediaItem(),
        )
        QueueCoordinator.consumePlayedUserQueue(player(items, index = 1))
        assertEquals(listOf("context-a", "user-b", "user-c"), items.map { it.mediaId })
    }
}
