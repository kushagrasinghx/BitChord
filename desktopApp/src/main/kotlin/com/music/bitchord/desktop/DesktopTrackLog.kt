package com.music.bitchord.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The few lines a resolve has to say about itself. */
internal object DesktopTrackLog {

    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** `$XDG_STATE_HOME/bitchord/desktop.log`, or the usual default under $HOME. */
    private val file: Path? by lazy {
        runCatching {
            val state = System.getenv("XDG_STATE_HOME")?.takeIf { it.isNotBlank() }
                ?: "${System.getProperty("user.home")}/.local/state"
            val directory = Path.of(state, "bitchord")
            Files.createDirectories(directory)
            directory.resolve("desktop.log")
        }.getOrNull()
    }

    fun log(line: String) {
        val stamped = "${LocalTime.now().format(clock)}  $line"
        println("BitChord: $stamped")
        val target = file ?: return
        runCatching {
            Files.writeString(
                target,
                stamped + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }
}
