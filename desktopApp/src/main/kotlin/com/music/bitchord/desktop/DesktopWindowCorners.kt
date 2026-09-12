package com.music.bitchord.desktop

import kotlinx.coroutines.delay

/**
 * Rounded corners for the window, on the platform that has to be asked for them.
 *
 * Windows 11 rounds a window with a system frame by itself. This one is undecorated there — it
 * draws its own title bar, see [DesktopWindowChrome] — and a frameless window is left square.
 * `DWMWA_WINDOW_CORNER_PREFERENCE` does not help: it rounds the frame the compositor draws, so for
 * this window it returns success and changes nothing. The window is clipped to a rounded region
 * instead, which has to be re-applied whenever the window changes size.
 *
 * Nothing to do anywhere else: Linux leaves decoration to the window manager, which rounds what it
 * chooses to round, and an app reaching past it would be taking a decision that is not its own.
 */
internal object DesktopWindowCorners {

    private val available: Boolean by lazy {
        DesktopPlatform.isWindows &&
            runCatching { DesktopAnalysisRuntime.loadNative(LIBRARY) }
                .onFailure { DesktopTrackLog.log("rounded corners unavailable: ${it.message}") }
                .isSuccess
    }

    /**
     * Rounds the window titled [title], or squares it off again when [rounded] is false.
     *
     * A maximized window is square: its edges are the screen's, and rounding them would leave the
     * desktop showing through the corners.
     *
     * Quiet when it cannot be had — a window that has not been shown yet is not there to find, and
     * that is not worth a line in the log every time.
     */
    suspend fun update(title: String, rounded: Boolean) {
        if (!available) return
        // The window is found by title, and it is not always on screen to be found at the moment
        // this is first asked — the composition runs ahead of the window being shown.
        repeat(ATTEMPTS) {
            if (runCatching { nativeSetCorners(title, rounded) }.getOrDefault(false)) return
            delay(RETRY_MILLIS)
        }
    }

    @JvmStatic
    private external fun nativeSetCorners(title: String, rounded: Boolean): Boolean

    private const val LIBRARY = "bitchord_smtc"

    private const val ATTEMPTS = 20
    private const val RETRY_MILLIS = 150L
}
