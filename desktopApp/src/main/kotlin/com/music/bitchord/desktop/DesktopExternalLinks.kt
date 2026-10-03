package com.music.bitchord.desktop

import java.awt.Desktop
import java.net.URI

object DesktopExternalLinks {
    /** Puts [text] on the system clipboard. */
    fun copy(text: String): Result<Unit> = runCatching {
        java.awt.Toolkit.getDefaultToolkit().systemClipboard
            .setContents(java.awt.datatransfer.StringSelection(text), null)
    }

    fun open(uri: String): Result<Unit> = runCatching {
        check(Desktop.isDesktopSupported()) { "This desktop environment cannot open links" }
        Desktop.getDesktop().browse(URI(uri))
    }
}
