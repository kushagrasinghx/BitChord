package com.music.bitchord.playback

import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class OutputRebuildOrderTest {

    private fun fakePlayer(name: String, events: MutableList<String>): ExoPlayer = Proxy.newProxyInstance(
        ExoPlayer::class.java.classLoader,
        arrayOf(ExoPlayer::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "release" -> events += "$name.release"
            "setPlayWhenReady" -> events += "$name.playWhenReady"
        }
        null
    } as ExoPlayer

    @Test
    fun `old players are released before the replacement starts`() {
        val events = mutableListOf<String>()
        val oldActive = fakePlayer("oldActive", events)
        val oldSpare = fakePlayer("oldSpare", events)
        val newActive = fakePlayer("newActive", events)

        retireThenStart(oldActive, oldSpare, newActive, playWhenReady = true)

        assertEquals(listOf("oldActive.release", "oldSpare.release", "newActive.playWhenReady"), events)
    }
}
