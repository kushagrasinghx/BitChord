package com.music.bitchord.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.Renderer
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class AutoMixAudioClockRendererTest {
    private var rendererDelayUs = 1_000_000L
    private var rendererState = Renderer.STATE_STARTED
    private var mixing = true
    private val delegate = Proxy.newProxyInstance(Renderer::class.java.classLoader, arrayOf(Renderer::class.java)) { _, method, args ->
        when (method.name) {
            "getDurationToProgressUs" -> {
                assertEquals(123_456L, args[0])
                assertEquals(456_789L, args[1])
                rendererDelayUs
            }
            "getState" -> rendererState
            else -> error("Unexpected renderer method ${method.name}")
        }
    } as Renderer
    private val renderer = AutoMixAudioClockRenderer(delegate) { mixing }

    private fun nextWake() = renderer.getDurationToProgressUs(123_456L, 456_789L)

    @Test
    fun capsRepeatedSlowWakeRequestsWithoutDelayingAlreadyFreshSamples() {
        for ((reported, expected) in listOf(1_000_000L to 30_000L, 260_000L to 30_000L, 30_000L to 30_000L, 9_000L to 9_000L, 0L to 0L)) {
            rendererDelayUs = reported
            assertEquals(expected, nextWake())
        }
    }

    @Test
    fun restoresDelegateCadenceWhenPausedOrOutsideAnAutoMixFade() {
        mixing = false
        assertEquals(rendererDelayUs, nextWake())
        mixing = true
        assertEquals(30_000L, nextWake())
        rendererState = Renderer.STATE_ENABLED
        assertEquals(rendererDelayUs, nextWake())
        rendererState = Renderer.STATE_STARTED
        assertEquals(30_000L, nextWake())
        mixing = false
        assertEquals(rendererDelayUs, nextWake())
    }
}
