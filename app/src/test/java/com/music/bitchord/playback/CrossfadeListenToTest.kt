package com.music.bitchord.playback

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class CrossfadeListenToTest {

    private fun fakePlayer(): Pair<ExoPlayer, MutableList<Player.Listener>> {
        val listeners = mutableListOf<Player.Listener>()
        val player = Proxy.newProxyInstance(
            ExoPlayer::class.java.classLoader,
            arrayOf(ExoPlayer::class.java),
        ) { _, method, args ->
            when (method.name) {
                "addListener" -> {
                    listeners += args[0] as Player.Listener
                    null
                }
                "removeListener" -> {
                    listeners -= args[0] as Player.Listener
                    null
                }
                else -> null
            }
        } as ExoPlayer
        return player to listeners
    }

    @Test
    fun `version swap moves the controller listener off the retired player`() {
        val (outgoing, outgoingListeners) = fakePlayer()
        val (incoming, incomingListeners) = fakePlayer()
        val controller = CrossfadeController(
            scope = TestScope(),
            active = { outgoing },
            standby = { incoming },
            onHandoff = { _, _ -> },
        )

        controller.listenTo(outgoing)
        assertEquals(1, outgoingListeners.size)
        assertEquals(0, incomingListeners.size)

        controller.listenTo(incoming)
        assertEquals(0, outgoingListeners.size)
        assertEquals(1, incomingListeners.size)
    }

    @Test
    fun `reattaching to the same player is idempotent`() {
        val (player, listeners) = fakePlayer()
        val controller = CrossfadeController(
            scope = TestScope(),
            active = { player },
            standby = { player },
            onHandoff = { _, _ -> },
        )
        controller.listenTo(player)
        controller.listenTo(player)
        assertEquals(1, listeners.size)
    }
}
