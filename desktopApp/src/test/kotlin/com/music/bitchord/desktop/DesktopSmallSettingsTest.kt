package com.music.bitchord.desktop

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The small Settings toggles ported from Android, each judged on what it actually changes. */
class DesktopSmallSettingsTest {

    // ── Filter non-music audio ────────────────────────────────────────────

    private val scratch = Files.createTempDirectory("bitchord-filter")

    @AfterTest
    fun cleanUp() {
        scratch.toFile().deleteRecursively()
    }

    private fun file(name: String, bytes: Int): java.nio.file.Path {
        val path = scratch.resolve(name)
        Files.createDirectories(path.parent)
        Files.write(path, ByteArray(bytes))
        return path
    }

    @Test
    fun `an ordinary track survives the filter`() {
        assertTrue(DesktopLocalMusic.isMusic(file("Artist - Song.mp3", 4_000_000)))
    }

    @Test
    fun `a WAV is dropped however big it is`() {
        assertFalse(DesktopLocalMusic.isMusic(file("Artist - Song.wav", 40_000_000)))
    }

    @Test
    fun `a file too small to hold thirty seconds is dropped`() {
        assertFalse(DesktopLocalMusic.isMusic(file("blip.mp3", 4_000)))
    }

    @Test
    fun `ringtones, alarms, notifications and recordings are dropped`() {
        listOf(
            "Ringtones/Marimba.mp3",
            "Notifications/ping.mp3",
            "Alarms/wake.mp3",
            "Recordings/meeting.mp3",
            "my voice note 3.mp3",
        ).forEach { name ->
            assertFalse(DesktopLocalMusic.isMusic(file(name, 4_000_000)), name)
        }
    }

}
