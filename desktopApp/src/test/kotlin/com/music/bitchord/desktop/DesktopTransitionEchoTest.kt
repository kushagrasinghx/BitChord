package com.music.bitchord.desktop

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopTransitionEchoTest {
    @Test
    fun `echo repeats on the requested beat and decays`() {
        val echo = DesktopTransitionEcho(sampleRate = 100, channels = 1).apply { configure(0.1) }
        val output = FloatArray(31)
        for (sample in output.indices) {
            output[sample] = echo.process(if (sample == 0) 1f else 0f, send = 1f, dry = 0f)
        }

        assertTrue(abs(output[10] - 0.5f) < 0.001f, "first repeat was ${output[10]}")
        assertTrue(abs(output[20] - 0.2f) < 0.001f, "second repeat was ${output[20]}")
        assertTrue(abs(output[30] - 0.08f) < 0.001f, "third repeat was ${output[30]}")
    }
}
