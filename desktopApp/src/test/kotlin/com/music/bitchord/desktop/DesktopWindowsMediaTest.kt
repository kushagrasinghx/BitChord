package com.music.bitchord.desktop

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which media key does what.
 *
 * The native side hands back a number and nothing else, so a transposed pair
 * here means the previous-track key skips forward — on a platform this test
 * does not run on, and with no compiler error to say so.
 */
class DesktopWindowsMediaTest {

    private val pressed = mutableListOf<String>()

    private fun install() {
        DesktopWindowsMedia.start(
            DesktopWindowsMedia.Controller(
                onPlay = { pressed += "play" },
                onPause = { pressed += "pause" },
                onNext = { pressed += "next" },
                onPrevious = { pressed += "previous" },
                onStop = { pressed += "stop" },
            ),
        )
    }

    @AfterTest
    fun tearDown() = DesktopWindowsMedia.stop()

    @Test
    fun everyButtonReachesItsOwnControl() {
        install()
        (0..4).forEach(DesktopWindowsMedia::onButton)
        assertEquals(listOf("play", "pause", "next", "previous", "stop"), pressed)
    }

    @Test
    fun anUnknownButtonIsIgnoredRatherThanGuessed() {
        install()
        DesktopWindowsMedia.onButton(99)
        DesktopWindowsMedia.onButton(-1)
        assertEquals(emptyList(), pressed)
    }
}
